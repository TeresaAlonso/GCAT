/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.print.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.obiba.magma.NoSuchVariableException;
import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueTable;
import org.obiba.magma.VariableValueSource;
import org.obiba.magma.type.DateTimeType;
import org.obiba.magma.type.TextType;
import org.obiba.onyx.core.domain.participant.Participant;
import org.obiba.onyx.core.io.support.LocalizedResourceLoader;
import org.obiba.onyx.core.service.ActiveInterviewService;
import org.obiba.onyx.magma.MagmaInstanceProvider;
import org.obiba.onyx.print.PdfTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;

import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

public class DefaultPdfTemplateEngine implements PdfTemplateEngine {

  private static final Logger log = LoggerFactory.getLogger(PdfTemplateReport.class);

  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

  private MagmaInstanceProvider magmaInstanceProvider;

  private MessageSource messageSource;

  public void setMagmaInstanceProvider(MagmaInstanceProvider magmaInstanceProvider) {
    this.magmaInstanceProvider = magmaInstanceProvider;
  }

  @Required
  public void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public InputStream applyTemplate(Locale locale, Map<String, String> fieldToVariableMap, LocalizedResourceLoader reportTemplateLoader, ActiveInterviewService activeInterviewService) {

    // Get report template
    Resource resource = reportTemplateLoader.getLocalizedResource(locale);

    // Read report template
    PdfReader pdfReader;
    try {
      pdfReader = new PdfReader(resource.getInputStream());
    } catch(Exception ex) {
      throw new RuntimeException("Report to participant template cannot be read", ex);
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PdfStamper stamper = null;

    // Set the values in the report data fields
    try {
      stamper = new PdfStamper(pdfReader, output);
      stamper.setFormFlattening(true);

      AcroFields form = stamper.getAcroFields();
      Participant participant = activeInterviewService.getParticipant();

      setVariableDataFields(participant, form, fieldToVariableMap, locale);
      setAdditionalDataFields(form);

    } catch(Exception ex) {
      throw new RuntimeException("An error occured while preparing the report to participant", ex);
    } finally {
      try {
        stamper.close();
      } catch(Exception e) {
        log.warn("Could not close PdfStamper", e);
      }
      try {
        output.close();
      } catch(IOException e) {
        log.warn("Could not close OutputStream", e);
      }
      pdfReader.close();
    }

    return new ByteArrayInputStream(output.toByteArray());
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = new SimpleDateFormat(dateFormat);
  }

  private void setAdditionalDataFields(AcroFields form) {
    try {
      form.setField("DateInterview\\.date", dateFormat.format(new Date()));
    } catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void setVariableDataFields(Participant participant, AcroFields form, Map<String, String> fieldToVariableMap, Locale locale) {

    HashMap<String, String> fieldList = form.getFields();

    try {
      // Iterate on each field of pdf template
      for(Entry<String, String> field : fieldList.entrySet()) {
        String[] keys = splitData(field.getKey());

        // TEMPORARY FIX so that "NA" is displayed in a field if the data is not available.
        // This should be replaced by a default value for the field in the PDF template,
        // however this didn't seem to work when I tested it (default values were not getting printed)
        // We need to find a way to fix that (might be a bug in Acrobat forms).
        form.setField(field.getKey(), "N/A");

        // Iterate on each key for one field of pdf template (for example when a variable depends on several
        // instruments)
        for(String variableKey : keys) {
          String variablePath = fieldToVariableMap.get(variableKey);

          if(variablePath != null) {
            try {
              ValueTable valueTable = magmaInstanceProvider.resolveTableFromVariablePath(variablePath);
              VariableValueSource variable = magmaInstanceProvider.resolveVariablePath(variablePath);
              ValueSet valueSet = valueTable.getValueSet(magmaInstanceProvider.newParticipantEntity(participant));
              String valueString = getValueAsString(variable.getVariable(), variable.getValue(valueSet), locale);
              if(valueString != null && valueString.length() != 0) {
                form.setField(field.getKey(), valueString);
                break;
              }
            } catch(NoSuchVariableException e) {
              log.error("Invalid PDF template definition. Field '{}' is linked to inexistent variable '{}'.", field.getKey(), variablePath);
              throw e;
            }
          }
        }
      }
    } catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private String getValueAsString(org.obiba.magma.Variable variable, Value value, Locale locale) {
    if(value.isNull()) return "";

    String valueString = "";
    if(value.isSequence()) {
      for(Value v : value.asSequence().getValues()) {
        valueString += " " + getValueAsString(variable, v, locale);
      }
    } else {
      valueString = value.toString();
      String unit = variable.getUnit();
      if(value.getValueType() == DateTimeType.get()) {
        valueString = dateFormat.format(value.getValue());
      } else if(value.getValueType() == TextType.get() && unit == null) {
        valueString = messageSource.getMessage(valueString, null, valueString, locale);
      } else {
        if(variable != null && unit != null) valueString += " " + unit;
      }
    }
    return valueString;
  }

  private String[] splitData(String string) {

    // By default, a field name in a PDF form starts with "form[0].page[0].", concatenated with the custom name that we
    // gave to this field
    String pattern = "([a-zA-Z0-9]+\\[+[0-9]+\\]+\\.){2}";

    // Delete "form[0].page[0]." string to keep only the custom name given to the variable
    String variable = string.replaceFirst(pattern, "");
    variable = variable.replaceAll("\\[[0-9]\\]", "");

    // If multiple instrument types are used for the same field, they are separated by "-"
    String[] list = variable.split("-");
    return list;
  }

}
