/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.webapp.base.page;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxIndicatorAware;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.obiba.onyx.core.service.UserSessionService;
import org.obiba.onyx.webapp.OnyxAuthenticatedSession;
import org.obiba.onyx.webapp.base.panel.HeaderPanel;
import org.obiba.onyx.webapp.base.panel.MenuBar;
import org.obiba.onyx.webapp.battery.BatteryLevelIndicator;
import org.obiba.onyx.wicket.reusable.ConfirmationDialog;
import org.obiba.onyx.wicket.reusable.Dialog;
import org.obiba.onyx.wicket.reusable.Dialog.CloseButtonCallback;
import org.obiba.onyx.wicket.reusable.Dialog.Option;
import org.obiba.onyx.wicket.reusable.Dialog.Status;
import org.obiba.onyx.wicket.reusable.DialogBuilder;
import org.obiba.onyx.wicket.reusable.PrintableReportPanel;
import org.obiba.onyx.wicket.reusable.ReusableDialogProvider;
import org.obiba.onyx.wicket.util.DateModelUtils;

public abstract class BasePage extends AbstractBasePage implements IAjaxIndicatorAware, ReusableDialogProvider {

  @SpringBean(name = "userSessionService")
  UserSessionService userSessionService;

  private final ConfirmationDialog reusableConfirmationDialog = new ConfirmationDialog("reusable-confirmation-dialog");

  private PrintableReportPanel printableReportPanel;

  private final Dialog reusablePrintDialog = DialogBuilder.buildDialog("reusable-print-dialog", new ResourceModel("PrintReport"), printableReportPanel = new PrintableReportPanel("content")).setOptions(Option.OK_CANCEL_OPTION, "PrintReports").getDialog();

  public BasePage() {
    super();

    add(reusableConfirmationDialog);

    reusablePrintDialog.addFormValidator(printableReportPanel.getFormValidator());
    reusablePrintDialog.setCloseButtonCallback(new CloseButtonCallback() {
      private static final long serialVersionUID = 1L;

      public boolean onCloseButtonClicked(AjaxRequestTarget target, Status status) {
        if(status == null || status.equals(Status.WINDOW_CLOSED)) {
          return true;
        } else if(status.equals(Status.SUCCESS)) {
          printableReportPanel.printReports();
          FeedbackPanel feedbackPanel = new FeedbackPanel("content");
          if(!feedbackPanel.anyErrorMessage()) {
            printableReportPanel.getFeedbackWindow().setCloseButtonCallback(new CloseButtonCallback() {

              private static final long serialVersionUID = 1L;

              public boolean onCloseButtonClicked(AjaxRequestTarget target1, Status status1) {
                // Close the print dialog when the user dismisses the success message.
                reusablePrintDialog.close(target1);
                return true;
              }

            });
          }
          printableReportPanel.getFeedbackWindow().setContent(feedbackPanel);
          printableReportPanel.getFeedbackWindow().show(target);

          return false;
        } else if(status.equals(Status.ERROR)) {
          printableReportPanel.getFeedbackWindow().setContent(new FeedbackPanel("content"));
          printableReportPanel.getFeedbackWindow().show(target);
          return false;
        }
        return true;
      }

    });

    reusablePrintDialog.setWidthUnit("em");
    reusablePrintDialog.setHeightUnit("em");
    reusablePrintDialog.setInitialHeight(34);
    reusablePrintDialog.setInitialWidth(50);
    add(reusablePrintDialog);

    ContextImage img = new ContextImage("logo", new Model<String>("images/logo/logo_on_dark.png"));
    img.setMarkupId("logo");
    img.setOutputMarkupId(true);

    Panel headerPanel = new EmptyPanel("header");

    Panel menuBar = new EmptyPanel("menuBar");
    menuBar.setMarkupId("menuBar");
    setOutputMarkupId(true);

    Label userFullName = new Label("userFullName");
    Label currentTime = new Label("currentTime");
    Panel battery = new EmptyPanel("battery");

    Session session = getSession();
    // Tests the session type for unit testing
    if(session instanceof OnyxAuthenticatedSession) {
      if(((OnyxAuthenticatedSession) getSession()).isSignedIn()) {
        headerPanel = new HeaderPanel("header");
        menuBar = new MenuBar("menuBar");

        userFullName.setDefaultModel(new Model(OnyxAuthenticatedSession.get().getUserName()));
        currentTime.setDefaultModel(DateModelUtils.getDateTimeModel(new Model<DateFormat>(new SimpleDateFormat("yyyy-MM-dd - HH:mm:ss", getLocale())), new Model<Date>(new Date())));
        battery = new BatteryLevelIndicator("battery");
      }
    }

    add(currentTime);
    add(battery);
    add(img);
    add(userFullName);
    add(headerPanel);
    add(menuBar);

    add(new Label("baseAjaxIndicator", new StringResourceModel("Processing", this, null)));
  }

  public void setMenuBarVisible(boolean visible) {
    get("menuBar").setVisible(visible);
  }

  /**
   * @see org.apache.wicket.ajax.IAjaxIndicatorAware#getAjaxIndicatorMarkupId()
   */
  public String getAjaxIndicatorMarkupId() {
    return "base-ajax-indicator";
  }

  /**
   * 
   * @param language
   * @param target
   */
  public void onLanguageUpdate(Locale language, AjaxRequestTarget target) {
    setResponsePage(getPage());
  }

  public ConfirmationDialog getConfirmationDialog() {
    return reusableConfirmationDialog;
  }

  public Dialog getPrintableReportsDialog() {
    return reusablePrintDialog;
  }

}
