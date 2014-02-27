/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.webapp.participant.page;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.Date;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.authorization.strategies.role.metadata.MetaDataRoleAuthorizationStrategy;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.value.ValueMap;
import org.obiba.core.service.EntityQueryService;
import org.obiba.onyx.core.domain.participant.InterviewStatus;
import org.obiba.onyx.core.domain.participant.Participant;
import org.obiba.onyx.core.service.ActiveInterviewService;
import org.obiba.onyx.core.service.InterviewManager;
import org.obiba.onyx.core.service.UserSessionService;
import org.obiba.onyx.engine.Action;
import org.obiba.onyx.engine.ActionDefinition;
import org.obiba.onyx.engine.ActionDefinitionConfiguration;
import org.obiba.onyx.engine.ActionType;
import org.obiba.onyx.engine.Stage;
import org.obiba.onyx.print.IPrintableReport;
import org.obiba.onyx.print.PrintableReportsRegistry;
import org.obiba.onyx.webapp.action.panel.InterviewLogPanel;
import org.obiba.onyx.webapp.action.panel.LoadableInterviewLogModel;
import org.obiba.onyx.webapp.base.page.BasePage;
import org.obiba.onyx.webapp.home.page.HomePage;
import org.obiba.onyx.webapp.participant.panel.AddCommentPanel;
import org.obiba.onyx.webapp.participant.panel.CommentsModalPanel;
import org.obiba.onyx.webapp.participant.panel.InterviewMenuBar;
import org.obiba.onyx.webapp.participant.panel.ParticipantPanel;
import org.obiba.onyx.webapp.stage.panel.StageSelectionPanel;
import org.obiba.onyx.wicket.action.ActionWindow;
import org.obiba.onyx.wicket.model.SpringStringResourceModel;
import org.obiba.onyx.wicket.reusable.AddCommentWindow;
import org.obiba.onyx.wicket.reusable.ConfirmationDialog;
import org.obiba.onyx.wicket.reusable.ConfirmationDialog.OnYesCallback;
import org.obiba.onyx.wicket.reusable.Dialog;
import org.obiba.onyx.wicket.reusable.Dialog.CloseButtonCallback;
import org.obiba.onyx.wicket.reusable.Dialog.Option;
import org.obiba.onyx.wicket.reusable.Dialog.Status;
import org.obiba.onyx.wicket.reusable.Dialog.WindowClosedCallback;
import org.obiba.onyx.wicket.reusable.DialogBuilder;
import org.obiba.onyx.wicket.util.DateModelUtils;
import org.obiba.wicket.markup.html.panel.KeyValueDataPanel;
import org.obiba.wicket.markup.html.table.DetachableEntityModel;

@SuppressWarnings("MagicNumber")
@AuthorizeInstantiation({ "SYSTEM_ADMINISTRATOR", "PARTICIPANT_MANAGER", "DATA_COLLECTION_OPERATOR" })
public class InterviewPage extends BasePage {

  @SpringBean(name = "printableReportsRegistry")
  private PrintableReportsRegistry printableReportsRegistry;

  @SpringBean(name = "userSessionService")
  private UserSessionService userSessionService;

  @SpringBean(name = "activeInterviewService")
  private ActiveInterviewService activeInterviewService;

  @SpringBean
  private ActionDefinitionConfiguration actionDefinitionConfiguration;

  @SpringBean
  private EntityQueryService queryService;

  private Dialog addCommentDialog;

  private Label commentsCount;

  AjaxLink<?> viewComments;

  @SpringBean
  private InterviewManager interviewManager;

  public InterviewPage() {

    if(activeInterviewService.getParticipant() == null || activeInterviewService.getInterview() == null) {
      setResponsePage(WebApplication.get().getHomePage());
    } else {

      addOrReplace(new EmptyPanel("header"));
      //
      // Modify menu bar.
      //
      remove("menuBar");
      add(new InterviewMenuBar("menuBar"));

      final InterviewLogPanel interviewLogPanel;
      final Dialog interviewLogsDialog;
      interviewLogPanel = new InterviewLogPanel("content", 338, new LoadableInterviewLogModel());
      interviewLogsDialog = DialogBuilder
          .buildDialog("interviewLogsDialog", new StringResourceModel("Log", this, null), interviewLogPanel)
          .setOptions(Option.CLOSE_OPTION).setFormCssClass("interview-log-dialog-form").getDialog();
      interviewLogsDialog.setHeightUnit("em");
      interviewLogsDialog.setWidthUnit("em");
      interviewLogsDialog.setInitialHeight(34);
      interviewLogsDialog.setInitialWidth(59);
      interviewLogsDialog.setCloseButtonCallback(new CloseButtonCallback() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean onCloseButtonClicked(AjaxRequestTarget target, Status status) {
          // Update comment count on interview page once the modal window closes. User may have added comments.
          updateCommentCount(target);
          return true;
        }
      });
      interviewLogPanel.setup(interviewLogsDialog);
      interviewLogPanel.add(new AttributeModifier("class", true, new Model<String>("interview-log-panel-content")));
      interviewLogPanel.setMarkupId("interviewLogPanel");
      interviewLogPanel.setOutputMarkupId(true);

      add(interviewLogsDialog);

      Participant participant = activeInterviewService.getParticipant();

      add(new ParticipantPanel("participant", new DetachableEntityModel<Participant>(queryService, participant), true));

      // Create modal comments window
      final ModalWindow commentsWindow;
      add(commentsWindow = new ModalWindow("addCommentsModal"));
      commentsWindow.setCssClassName("onyx");
      commentsWindow.setTitle(new StringResourceModel("CommentsWindow", this, null));
      commentsWindow.setHeightUnit("em");
      commentsWindow.setWidthUnit("em");
      commentsWindow.setInitialHeight(34);
      commentsWindow.setInitialWidth(50);

      commentsWindow.setWindowClosedCallback(new ModalWindow.WindowClosedCallback() {

        private static final long serialVersionUID = 1L;

        @Override
        public void onClose(AjaxRequestTarget target) {

        }
      });

      ViewInterviewLogsLink viewCommentsIconLink = createIconLink("viewCommentsIconLink", interviewLogsDialog,
          interviewLogPanel, true);
      add(viewCommentsIconLink);
      ContextImage commentIcon = createContextImage("commentIcon", "icons/note.png");
      viewCommentsIconLink.add(commentIcon);

      ViewInterviewLogsLink viewLogIconLink = createIconLink("viewLogIconLink", interviewLogsDialog, interviewLogPanel,
          false);
      add(viewLogIconLink);
      ContextImage logIcon = createContextImage("logIcon", "icons/loupe_button.png");
      viewLogIconLink.add(logIcon);

      // Add view interview comments action
      add(viewComments = new ViewInterviewLogsLink("viewComments", interviewLogsDialog, interviewLogPanel, true) {

        private static final long serialVersionUID = -5561038138085317724L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          interviewLogPanel.setCommentsOnly(true);

          // Disable Show All Button
          target.appendJavascript(
              "$('[name=showAll]').attr('disabled','true');$('[name=showAll]').css('color','rgba(0, 0, 0, 0.2)');$('[name=showAll]').css('border-color','rgba(0, 0, 0, 0.2)');");
          super.onClick(target);
        }

      });
      add(new ViewInterviewLogsLink("viewLog", interviewLogsDialog, interviewLogPanel, false) {

        private static final long serialVersionUID = -5561038138085317724L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          interviewLogPanel.setCommentsOnly(false);

          // Disable Show All Button
          target.appendJavascript(
              "$('[name=showAll]').attr('disabled','true');$('[name=showAll]').css('color','rgba(0, 0, 0, 0.2)');$('[name=showAll]').css('border-color','rgba(0, 0, 0, 0.2)');");
          super.onClick(target);
        }

      });

      // Add create interview comments action
      add(addCommentDialog = createAddCommentDialog());
      add(new AjaxLink("addComment") {
        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          addCommentDialog.show(target);
        }
      });

      // Initialize comments counter
      updateCommentsCount();

      ActiveInterviewModel interviewModel = new ActiveInterviewModel();

      KeyValueDataPanel kvPanel = new KeyValueDataPanel("interview");
      kvPanel.addRow(new StringResourceModel("StartDate", this, null), new PropertyModel(interviewModel, "startDate"));
      kvPanel.addRow(new StringResourceModel("EndDate", this, null), new PropertyModel(interviewModel, "endDate"));
      kvPanel.addRow(new StringResourceModel("Status", this, null), new PropertyModel(interviewModel, "status"));
      add(kvPanel);

      // Interview cancellation
      final ActionDefinition cancelInterviewDef = actionDefinitionConfiguration
          .getActionDefinition(ActionType.STOP, "Interview", null, null);
      final ActionWindow interviewActionWindow = new ActionWindow("modal") {

        private static final long serialVersionUID = 1L;

        @Override
        public void onActionPerformed(AjaxRequestTarget target, Stage stage, Action action) {
          activeInterviewService.setStatus(InterviewStatus.CANCELLED);
          setResponsePage(InterviewPage.class);
        }

      };
      add(interviewActionWindow);

      AjaxLink<?> link = new AjaxLink("cancelInterview") {

        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          Label label = new Label("content",
              new StringResourceModel("ConfirmCancellationOfInterview", InterviewPage.this, null));
          label.add(new AttributeModifier("class", true, new Model<String>("confirmation-dialog-content")));
          ConfirmationDialog confirmationDialog = getConfirmationDialog();

          confirmationDialog.setContent(label);
          confirmationDialog.setTitle(new StringResourceModel("ConfirmCancellationOfInterviewTitle", this, null));
          confirmationDialog.setYesButtonCallback(new OnYesCallback() {

            private static final long serialVersionUID = -6691702933562884991L;

            @Override
            public void onYesButtonClicked(AjaxRequestTarget target) {
              interviewActionWindow.show(target, null, cancelInterviewDef);
            }

          });
          confirmationDialog.show(target);
        }

        @Override
        public boolean isVisible() {
          InterviewStatus status = activeInterviewService.getInterview().getStatus();
          return status == InterviewStatus.IN_PROGRESS || status == InterviewStatus.COMPLETED;
        }
      };
      MetaDataRoleAuthorizationStrategy.authorize(link, RENDER, "PARTICIPANT_MANAGER");
      add(link);

      // Interview closing
      final ActionDefinition closeInterviewDef = actionDefinitionConfiguration
          .getActionDefinition(ActionType.STOP, "Closed.Interview", null, null);
      final ActionWindow closeInterviewActionWindow = new ActionWindow("closeModal") {

        private static final long serialVersionUID = 1L;

        @Override
        public void onActionPerformed(AjaxRequestTarget target, Stage stage, Action action) {
          activeInterviewService.setStatus(InterviewStatus.CLOSED);
          setResponsePage(InterviewPage.class);
        }

      };
      add(closeInterviewActionWindow);

      AjaxLink<?> closeLink = new AjaxLink("closeInterview") {

        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          Label label = new Label("content",
              new StringResourceModel("ConfirmClosingOfInterview", InterviewPage.this, null));
          label.add(new AttributeModifier("class", true, new Model<String>("confirmation-dialog-content")));
          ConfirmationDialog confirmationDialog = getConfirmationDialog();

          confirmationDialog.setContent(label);
          confirmationDialog.setTitle(new StringResourceModel("ConfirmClosingOfInterviewTitle", this, null));
          confirmationDialog.setYesButtonCallback(new OnYesCallback() {

            private static final long serialVersionUID = -6691702933562884991L;

            @Override
            public void onYesButtonClicked(AjaxRequestTarget target) {
              closeInterviewActionWindow.show(target, null, closeInterviewDef);
            }

          });
          confirmationDialog.show(target);
        }

        @Override
        public boolean isVisible() {
          InterviewStatus status = activeInterviewService.getInterview().getStatus();
          return status == InterviewStatus.IN_PROGRESS;
        }
      };
      MetaDataRoleAuthorizationStrategy.authorize(link, RENDER, "PARTICIPANT_MANAGER");
      add(closeLink);

      // Reinstate interview link
      add(createReinstateInterviewLink());

      // Print report link
      class ReportLink extends AjaxLink {

        private static final long serialVersionUID = 1L;

        ReportLink(String id) {
          super(id);
        }

        @Override
        public void onClick(AjaxRequestTarget target) {
          getPrintableReportsDialog().show(target);
        }
      }

      ReportLink printReportLink = new ReportLink("printReport");
      printReportLink.add(new Label("reportLabel", new ResourceModel("PrintReport")));
      add(printReportLink);

      add(new StageSelectionPanel("stage-list", getFeedbackWindow()) {

        private static final long serialVersionUID = 1L;

        @Override
        public void onViewComments(AjaxRequestTarget target, String stage) {

          commentsWindow.setContent(new CommentsModalPanel("content", commentsWindow, stage) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onAddComments(AjaxRequestTarget target) {
              updateCommentsCount();
              target.addComponent(commentsCount);
            }

          });
          commentsWindow.show(target);
        }

        @Override
        public void onViewLogs(AjaxRequestTarget target, String stage, boolean commentsOnly) {
          interviewLogPanel.setStageName(stage);
          interviewLogPanel.setCommentsOnly(commentsOnly);
          interviewLogPanel.setReadOnly(true);
          interviewLogPanel.addInterviewLogComponent();

          interviewLogsDialog.show(target);
        }

        @Override
        public void onActionPerformed(AjaxRequestTarget target, Stage stage, Action action) {
          if(activeInterviewService.getStageExecution(action.getStage()).isFinal()) {
            setResponsePage(InterviewPage.class);
          } else {
            updateCommentsCount();
            target.addComponent(commentsCount);
          }

        }

      });

      AjaxLink<?> exitLink = new AjaxLink("exitInterview") {

        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          interviewManager.releaseInterview();
          setResponsePage(HomePage.class);
        }
      };
      add(exitLink);

    }
  }

  public void updateCommentsCount() {
    viewComments.addOrReplace(commentsCount = new Label("commentsCount",
        String.valueOf(activeInterviewService.getInterviewComments().size())));
    commentsCount.setOutputMarkupId(true);
  }

  private AjaxLink<?> createReinstateInterviewLink() {
    String stateName = null;
    if(activeInterviewService.getInterview().getStatus() == InterviewStatus.CANCELLED) {
      stateName = "Cancelled";
    } else if(activeInterviewService.getInterview().getStatus() == InterviewStatus.CLOSED) {
      stateName = "Closed";
    }

    final ActionDefinition reinstateInterviewDef = actionDefinitionConfiguration
        .getActionDefinition(ActionType.EXECUTE, stateName, null, null);

    final ActionWindow reinstateInterviewActionWindow = new ActionWindow("reinstateModal") {

      private static final long serialVersionUID = 1L;

      @Override
      public void onActionPerformed(AjaxRequestTarget target, Stage stage, Action action) {
        activeInterviewService.reinstateInterview();
        setResponsePage(InterviewPage.class);
      }

    };
    add(reinstateInterviewActionWindow);

    AjaxLink<?> reinstateLink = new AjaxLink("reinstateInterview") {

      private static final long serialVersionUID = 1L;

      @Override
      public void onClick(AjaxRequestTarget target) {
        Label label = new Label("content",
            new StringResourceModel("ConfirmReinstatementOfInterview", InterviewPage.this, null));
        label.add(new AttributeModifier("class", true, new Model<String>("confirmation-dialog-content")));
        ConfirmationDialog confirmationDialog = getConfirmationDialog();

        confirmationDialog.setContent(label);
        confirmationDialog.setTitle(new StringResourceModel("ConfirmReinstatementOfInterviewTitle", this, null));
        confirmationDialog.setYesButtonCallback(new OnYesCallback() {

          private static final long serialVersionUID = -6691702933562884991L;

          @Override
          public void onYesButtonClicked(AjaxRequestTarget target) {
            reinstateInterviewActionWindow.show(target, null, reinstateInterviewDef);
          }

        });
        confirmationDialog.show(target);
      }

      @Override
      public boolean isVisible() {
        InterviewStatus status = activeInterviewService.getInterview().getStatus();
        return status == InterviewStatus.CANCELLED || status == InterviewStatus.CLOSED;
      }
    };

    MetaDataRoleAuthorizationStrategy.authorize(reinstateLink, RENDER, "PARTICIPANT_MANAGER");

    return reinstateLink;
  }

  private AddCommentWindow createAddCommentDialog() {
    AddCommentWindow dialog = new AddCommentWindow("addCommentDialog");
    final AddCommentPanel dialogContent = new AddCommentPanel("content");
    dialog.setContent(dialogContent);

    dialog.setWindowClosedCallback(new WindowClosedCallback() {

      private static final long serialVersionUID = 1L;

      @Override
      public void onClose(AjaxRequestTarget target, Status status) {
        if(status == Status.SUCCESS) {
          Action comment = (Action) dialogContent.getDefaultModelObject();
          activeInterviewService.doAction(null, comment);
          updateCommentsCount();
          target.addComponent(commentsCount);
        }
        dialogContent.clearCommentField();
      }
    });

    return dialog;
  }

  public void updateCommentCount(AjaxRequestTarget target) {
    updateCommentsCount();
    target.addComponent(commentsCount);
  }

  private class ViewInterviewLogsLink extends AjaxLink {

    private static final long serialVersionUID = -2193340839515835159L;

    private final Dialog interviewLogsDialog;

    private final InterviewLogPanel interviewLogPanel;

    private final boolean commentsOnly;

    private ViewInterviewLogsLink(String id, Dialog interviewLogsDialog, InterviewLogPanel interviewLogPanel,
        boolean commentsOnly) {
      super(id);
      this.interviewLogsDialog = interviewLogsDialog;
      this.interviewLogPanel = interviewLogPanel;
      this.commentsOnly = commentsOnly;
    }

    @Override
    public void onClick(AjaxRequestTarget target) {
      interviewLogPanel.setStageName(null);
      interviewLogPanel.setCommentsOnly(commentsOnly);
      interviewLogPanel.setReadOnly(false);
      interviewLogPanel.addInterviewLogComponent();

      interviewLogsDialog.show(target);
    }

  }

  @SuppressWarnings("serial")
  private class ActiveInterviewModel implements Serializable {

    public String getStartDate() {
      DateFormat dateTimeFormat = userSessionService.getDateTimeFormat();
      Date date = activeInterviewService.getInterview().getStartDate();

      return date == null
          ? ""
          : DateModelUtils.getDateTimeModel(new Model<DateFormat>(dateTimeFormat), new Model<Date>(date)).getObject();
    }

    public String getEndDate() {
      DateFormat dateTimeFormat = userSessionService.getDateTimeFormat();
      Date date = activeInterviewService.getInterview().getEndDate();

      return date == null
          ? ""
          : DateModelUtils.getDateTimeModel(new Model<DateFormat>(dateTimeFormat), new Model<Date>(date)).getObject();
    }

    public String getStatus() {
      Action act = activeInterviewService.getStatusAction();

      // act.getStage() == null => action is on interview and not on a stage
      if(act != null && act.getStage() == null && act.getEventReason() != null) {
        String reason = new SpringStringResourceModel(act.getEventReason()).getString();
        ValueMap map = new ValueMap("reason=" + reason);
        return new StringResourceModel(
            "InterviewStatus." + activeInterviewService.getInterview().getStatus() + ".WithReason", InterviewPage.this,
            new Model<ValueMap>(map)).getString();
      }
      return new StringResourceModel("InterviewStatus." + activeInterviewService.getInterview().getStatus(),
          InterviewPage.this, null).getString();
    }
  }

  private ViewInterviewLogsLink createIconLink(String id, Dialog dialog, InterviewLogPanel interviewLogPanel,
      boolean commentsOnly) {
    ViewInterviewLogsLink viewCommentsIconLink = new ViewInterviewLogsLink(id, dialog, interviewLogPanel,
        commentsOnly) {

      private static final long serialVersionUID = 1L;

      @Override
      public void onClick(AjaxRequestTarget target) {
        // Disable Show All Button
        target.appendJavascript(
            "$('[name=showAll]').attr('disabled','true');$('[name=showAll]').css('color','rgba(0, 0, 0, 0.2)');$('[name=showAll]').css('border-color','rgba(0, 0, 0, 0.2)');");
        super.onClick(target);
      }

    };
    viewCommentsIconLink.setMarkupId(id);
    viewCommentsIconLink.setOutputMarkupId(true);
    return viewCommentsIconLink;
  }

  private ContextImage createContextImage(String id, String image) {
    ContextImage commentIcon = new ContextImage(id, new Model<String>(image));
    commentIcon.setMarkupId(id);
    commentIcon.setOutputMarkupId(true);
    return commentIcon;
  }

  private class PrintableReportModel extends LoadableDetachableModel<IPrintableReport> {

    private static final long serialVersionUID = 1L;

    String name;

    private PrintableReportModel(IPrintableReport report) {
      super(report);
      name = report.getName();
    }

    @Override
    protected IPrintableReport load() {
      return printableReportsRegistry.getReportByName(name);
    }
  }

}
