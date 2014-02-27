/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.core.etl.participant.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.obiba.onyx.core.domain.participant.ParticipantAttribute;
import org.obiba.onyx.core.domain.participant.ParticipantMetadata;
import org.obiba.onyx.core.etl.participant.IParticipantReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public abstract class AbstractParticipantReader implements IParticipantReader {

  private static final Logger log = LoggerFactory.getLogger(AbstractParticipantReader.class);

  private ParticipantMetadata participantMetadata;

  // Maps Excel column names to attribute names.
  protected Map<String, String> columnNameToAttributeNameMap;

  // Maps attribute names to column indices.
  // TODO this is dedicated to csv/xls, should not be here but in its child (that works with xls)
  protected Map<String, Integer> attributeNameToColumnIndexMap;

  protected void checkColumnsForMandatoryAttributesPresent() {
    List<ParticipantAttribute> allAttributes = new ArrayList<ParticipantAttribute>();
    allAttributes.addAll(participantMetadata.getEssentialAttributes());
    allAttributes.addAll(participantMetadata.getConfiguredAttributes());

    // Check that all attributes mandatory at enrolment are present.
    for(ParticipantAttribute attribute : allAttributes) {
      if(attribute.isMandatoryAtEnrollment()) {
        if(!attributeNameToColumnIndexMap.containsKey(attribute.getName())) {
          throw new IllegalArgumentException("Invalid worksheet; no column exists for mandatory field '" + attribute.getName() + "'");
        }
      }
    }
  }

  public void setParticipantMetadata(ParticipantMetadata participantMetadata) {
    this.participantMetadata = participantMetadata;
    if(participantMetadata != null) {
      addDefaultColumnNameToAttributeNameMapEntries();
    }
  }

  @SuppressWarnings("unchecked")
  public void setColumnNameToAttributeNameMap(Map<String, String> columnNameToAttributeNameMap) {
    if(this.columnNameToAttributeNameMap == null) {
      this.columnNameToAttributeNameMap = new CaseInsensitiveMap();
    }
    if(columnNameToAttributeNameMap != null) {
      this.columnNameToAttributeNameMap.putAll(columnNameToAttributeNameMap);
    }
  }

  /**
   * Set the column name to attribute name map with a configuration string.
   * 
   * @param keyValuePairs list of key/value pairs separated by a comma. For example, "<code>param1=foo,param2=bar</code>
   * ".
   */
  public void setColumnToAttribute(String keyValuePairs) {
    if(columnNameToAttributeNameMap != null) {
      // Get list of strings separated by the delimiter
      StringTokenizer tokenizer = new StringTokenizer(keyValuePairs, ",");
      while(tokenizer.hasMoreElements()) {
        String token = tokenizer.nextToken();
        String[] entry = token.split("=");
        if(entry.length == 2) {
          columnNameToAttributeNameMap.put(entry[0].trim(), entry[1].trim());
        } else {
          log.error("Could not identify Participant column to attribute mapping: " + token);
        }
      }
    }
  }

  public ParticipantMetadata getParticipantMetadata() {
    return participantMetadata;
  }

  @SuppressWarnings("unchecked")
  private void addDefaultColumnNameToAttributeNameMapEntries() {
    if(columnNameToAttributeNameMap == null) {
      columnNameToAttributeNameMap = new CaseInsensitiveMap();
    }

    // Set default mappings for essential attributes.
    for(ParticipantAttribute essentialAttribute : participantMetadata.getEssentialAttributes()) {
      if(!essentialAttribute.isAssignableAtEnrollment()) {
        continue;
      }

      String essentialAttributeName = essentialAttribute.getName();

      if(!columnNameToAttributeNameMap.containsValue(essentialAttributeName)) {
        columnNameToAttributeNameMap.put(essentialAttributeName, essentialAttributeName);
      }
    }

    // Set default mappings for configured attributes.
    for(ParticipantAttribute configuredAttribute : participantMetadata.getConfiguredAttributes()) {
      String configuredAttributeName = configuredAttribute.getName();

      if(!columnNameToAttributeNameMap.containsValue(configuredAttributeName)) {
        columnNameToAttributeNameMap.put(configuredAttributeName, configuredAttributeName);
      }
    }
  }

}
