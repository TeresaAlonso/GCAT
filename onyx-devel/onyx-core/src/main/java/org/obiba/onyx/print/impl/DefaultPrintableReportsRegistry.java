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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.obiba.onyx.print.IPrintableReport;
import org.obiba.onyx.print.PrintableReportsRegistry;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Discovers and maintains the set of available Printable Reports, contributed by the various Onyx modules.
 */
public class DefaultPrintableReportsRegistry implements PrintableReportsRegistry, ApplicationContextAware {

  private ApplicationContext applicationContext;

  public Set<IPrintableReport> availableReports() {
    return getReportsFromApplicationContext();
  }

  public IPrintableReport getReportByName(String reportName) {
    for(IPrintableReport report : getReportsFromApplicationContext()) {
      if(report.getName().equals(reportName)) {
        return report;
      }
    }
    return null;
  }

  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  protected Set<IPrintableReport> getReportsFromApplicationContext() {
    Map<String, IPrintableReport> printableReports = applicationContext.getBeansOfType(IPrintableReport.class);
    Set<IPrintableReport> availableReports = new HashSet<IPrintableReport>();
    availableReports.addAll(printableReports.values());
    return availableReports;
  }

}
