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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.IRequestTarget;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.authorization.Action;
import org.apache.wicket.authorization.strategies.role.annotations.AuthorizeAction;
import org.apache.wicket.authorization.strategies.role.annotations.AuthorizeActions;
import org.apache.wicket.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.authorization.strategies.role.metadata.MetaDataRoleAuthorizationStrategy;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.behavior.IBehavior;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.basic.MultiLineLabel;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.markup.repeater.ReuseIfModelsEqualStrategy;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.value.ValueMap;
import org.obiba.core.service.EntityQueryService;
import org.obiba.core.service.PagingClause;
import org.obiba.core.service.SortingClause;
import org.obiba.onyx.core.domain.participant.Appointment;
import org.obiba.onyx.core.domain.participant.Interview;
import org.obiba.onyx.core.domain.participant.InterviewStatus;
import org.obiba.onyx.core.domain.participant.Participant;
import org.obiba.onyx.core.domain.participant.ParticipantFactory;
import org.obiba.onyx.core.domain.participant.ParticipantMetadata;
import org.obiba.onyx.core.domain.participant.RecruitmentType;
import org.obiba.onyx.core.domain.user.Role;
import org.obiba.onyx.core.service.ApplicationConfigurationService;
import org.obiba.onyx.core.service.InterviewManager;
import org.obiba.onyx.core.service.ParticipantService;
import org.obiba.onyx.core.service.UserSessionService;
import org.obiba.onyx.core.service.impl.NoSuchParticipantException;
import org.obiba.onyx.core.service.impl.ParticipantRegistryLookupException;
import org.obiba.onyx.engine.variable.export.OnyxDataExport;
import org.obiba.onyx.engine.variable.impl.ParticipantCaptureAndExportStrategy;
import org.obiba.onyx.webapp.base.page.BasePage;
import org.obiba.onyx.webapp.participant.panel.EditParticipantPanel;
import org.obiba.onyx.webapp.participant.panel.ParticipantPanel;
import org.obiba.onyx.webapp.participant.panel.ParticipantRegistryPanel;
import org.obiba.onyx.webapp.participant.panel.UnlockInterviewPanel;
import org.obiba.onyx.wicket.behavior.DisplayTooltipBehaviour;
import org.obiba.onyx.wicket.behavior.ExecuteJavaScriptBehaviour;
import org.obiba.onyx.wicket.panel.OnyxEntityList;
import org.obiba.onyx.wicket.reusable.ConfirmationDialog;
import org.obiba.onyx.wicket.reusable.ConfirmationDialog.OnYesCallback;
import org.obiba.onyx.wicket.reusable.Dialog;
import org.obiba.onyx.wicket.reusable.Dialog.CloseButtonCallback;
import org.obiba.onyx.wicket.reusable.Dialog.Option;
import org.obiba.onyx.wicket.reusable.Dialog.OptionSide;
import org.obiba.onyx.wicket.reusable.Dialog.Status;
import org.obiba.onyx.wicket.reusable.Dialog.WindowClosedCallback;
import org.obiba.onyx.wicket.reusable.DialogBuilder;
import org.obiba.onyx.wicket.util.DateModelUtils;
import org.obiba.wicket.markup.html.table.DetachableEntityModel;
import org.obiba.wicket.markup.html.table.IColumnProvider;
import org.obiba.wicket.markup.html.table.SortableDataProviderEntityServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@AuthorizeInstantiation({ "SYSTEM_ADMINISTRATOR", "PARTICIPANT_MANAGER", "PARTICIPANT_RECEPTIONIST", "DATA_COLLECTION_OPERATOR" })
public class ParticipantSearchPage extends BasePage {

  @SpringBean
  private EntityQueryService queryService;

  @SpringBean(name = "userSessionService")
  private UserSessionService userSessionService;

  @SpringBean
  private ParticipantService participantService;

  @SpringBean
  private InterviewManager interviewManager;

  @SpringBean
  private ParticipantMetadata participantMetadata;

  @SpringBean
  private ParticipantCaptureAndExportStrategy participantCaptureAndExportStrategy;

  @SpringBean
  private ApplicationConfigurationService applicationConfigurationService;

  @SpringBean
  private ParticipantFactory participantFactory;

  private OnyxEntityList<Participant> participantList;

  private Participant template = new Participant();

  private Dialog participantDetailsModalWindow;

  private Dialog editParticipantDetailsModalWindow;

  private Dialog unlockInterviewWindow;

  private UnlockInterviewPanel content;

  private static final int DEFAULT_INITIAL_HEIGHT = 13;

  private static final int DEFAULT_INITIAL_WIDTH = 34;

  private static final int DEFAULT_PARTICIPANT_HEIGHT = 36;

  private static final int DEFAULT_PARTICIPANT_WIDTH = 40;

  private UpdateParticipantListWindow updateParticipantListWindow;

  private ParticipantRegistryPanel participantRegistryPanel;

  private static final Logger log = LoggerFactory.getLogger(ParticipantSearchPage.class);

  public ParticipantSearchPage() {
    super();

    add(participantDetailsModalWindow = createParticipantDialog("participantDetailsModalWindow"));
    participantDetailsModalWindow.setHeightUnit("em");
    participantDetailsModalWindow.setInitialHeight(DEFAULT_PARTICIPANT_HEIGHT);
    participantDetailsModalWindow.setWidthUnit("em");
    participantDetailsModalWindow.setInitialWidth(DEFAULT_PARTICIPANT_WIDTH);
    add(editParticipantDetailsModalWindow = createParticipantDialog("editParticipantDetailsModalWindow"));
    editParticipantDetailsModalWindow.setTitle(new StringResourceModel("EditParticipantInfo", this, null));
    editParticipantDetailsModalWindow.setOptions(Option.OK_CANCEL_OPTION, "Save");
    editParticipantDetailsModalWindow.setHeightUnit("em");
    editParticipantDetailsModalWindow.setInitialHeight(DEFAULT_PARTICIPANT_HEIGHT);
    editParticipantDetailsModalWindow.setWidthUnit("em");
    editParticipantDetailsModalWindow.setInitialWidth(DEFAULT_PARTICIPANT_WIDTH);

    unlockInterviewWindow = new Dialog("unlockInterview");
    unlockInterviewWindow.setTitle(new ResourceModel("UnlockInterview"));
    unlockInterviewWindow.setOptions(Dialog.Option.YES_NO_CANCEL_OPTION);
    unlockInterviewWindow.setHeightUnit("em");
    unlockInterviewWindow.setWidthUnit("em");
    unlockInterviewWindow.setInitialHeight(DEFAULT_INITIAL_HEIGHT);
    unlockInterviewWindow.setInitialWidth(DEFAULT_INITIAL_WIDTH);

    unlockInterviewWindow.setCloseButtonCallback(new Dialog.CloseButtonCallback() {
      private static final long serialVersionUID = 1L;

      public boolean onCloseButtonClicked(AjaxRequestTarget target, Status status) {

        if(status.equals(Dialog.Status.YES)) {
          interviewManager.overrideInterview(content.getParticipant());
          setResponsePage(InterviewPage.class);
        }

        return true;
      }
    });

    add(unlockInterviewWindow);

    Form<Void> form = new Form<Void>("searchForm");
    add(form);

    form.add(new TextField<String>("inputField", new Model<String>(new String())));

    AjaxButton searchByInputField = new AjaxButton("searchByInputField", form) {
      @Override
      protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
        ParticipantEntityList replacement;
        String inputField = form.get("inputField").getDefaultModelObjectAsString();

        if(inputField == null) {
          replacement = getAllParticipantsList();
        } else {
          replacement = new ParticipantEntityList("participant-list", new ParticipantByInputFieldProvider(inputField), new ParticipantListColumnProvider(), new StringResourceModel("ParticipantsByInputField", ParticipantSearchPage.this, new Model<ValueMap>(new ValueMap("inputField=" + inputField))));
          replacement.setItemReuseStrategy(ReuseIfModelsEqualStrategy.getInstance());
        }
        replaceParticipantList(target, replacement);
      }

      @Override
      protected void onError(AjaxRequestTarget target, Form<?> form) {
        showFeedback(target);
      }

    };
    searchByInputField.setMarkupId("searchByInputField");
    form.add(searchByInputField);

    form.add(new AjaxButton("submit", form) {

      @Override
      protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
        ParticipantEntityList replacement = getAllParticipantsList();
        replaceParticipantList(target, replacement);
      }

      @Override
      protected void onError(AjaxRequestTarget target, Form<?> form) {
        showFeedback(target);
      }

    });

    form.add(new AjaxButton("appointments", form) {

      @Override
      protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
        ParticipantEntityList replacement = new ParticipantEntityList("participant-list", new AppointedParticipantProvider(template), new ParticipantListColumnProvider(), new StringResourceModel("AppointmentsOfTheDay", ParticipantSearchPage.this, null));
        replacement.setItemReuseStrategy(ReuseIfModelsEqualStrategy.getInstance());
        replaceParticipantList(target, replacement);
      }

      @Override
      protected void onError(AjaxRequestTarget target, Form<?> form) {
        showFeedback(target);
      }

    });

    form.add(new AjaxButton("interviews", form) {

      @Override
      protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
        ParticipantEntityList replacement = new ParticipantEntityList("participant-list", new InterviewedParticipantProvider(), new ParticipantListColumnProvider(), new StringResourceModel("CurrentInterviews", ParticipantSearchPage.this, null));
        replacement.setItemReuseStrategy(ReuseIfModelsEqualStrategy.getInstance());
        replaceParticipantList(target, replacement);
      }

      @Override
      protected void onError(AjaxRequestTarget target, Form<?> form) {
        showFeedback(target);
      }

    });

    add(new ActionFragment("actions"));

    participantList = new ParticipantEntityList("participant-list", new AppointedParticipantProvider(template), new ParticipantListColumnProvider(), new StringResourceModel("AppointmentsOfTheDay", ParticipantSearchPage.this, null));
    participantList.setItemReuseStrategy(ReuseIfModelsEqualStrategy.getInstance());
    add(participantList);

    updateParticipantListWindow = new UpdateParticipantListWindow("updateParticipantListWindow");
    updateParticipantListWindow.setWindowClosedCallback(new WindowClosedCallback() {
      public void onClose(AjaxRequestTarget target, Dialog.Status status) {
        if(Dialog.Status.SUCCESS.equals(status)) {
          ParticipantEntityList replacement = getAllParticipantsList();
          replacement.setItemReuseStrategy(ReuseIfModelsEqualStrategy.getInstance());
          replaceParticipantList(target, replacement);
        } else {
          updateParticipantListWindow.close(target);
        }
      }
    });
    updateParticipantListWindow.setCloseButtonCallback(new CloseButtonCallback() {
      public boolean onCloseButtonClicked(AjaxRequestTarget target, Status status) {
        return true;
      }
    });
    add(updateParticipantListWindow);
  }

  private Dialog createParticipantDialog(String id) {
    Dialog participantDialog = new Dialog(id);
    participantDialog.setTitle(new StringResourceModel("Participant", this, null));
    participantDialog.setHeightUnit("em");
    participantDialog.setWidthUnit("em");
    participantDialog.setInitialHeight(45);
    participantDialog.setInitialWidth(43);
    participantDialog.setType(Dialog.Type.PLAIN);
    participantDialog.setOptions(Dialog.Option.CLOSE_OPTION);
    return participantDialog;
  }

  @Override
  protected void onModelChanged() {
    super.onModelChanged();
    this.participantList.modelChanged();
  }

  private ParticipantEntityList getAllParticipantsList() {
    ParticipantEntityList participantList = new ParticipantEntityList("participant-list", new ParticipantProvider(), new ParticipantListColumnProvider(), new StringResourceModel("Participants", ParticipantSearchPage.this, null));
    participantList.setItemReuseStrategy(ReuseIfModelsEqualStrategy.getInstance());
    return participantList;
  }

  private void replaceParticipantList(AjaxRequestTarget target, ParticipantEntityList replacement) {
    participantList.replaceWith(replacement);
    participantList = replacement;

    target.addComponent(participantList);
    target.appendJavascript("styleParticipantSearchNavigationBar();");
  }

  private void showFeedback(AjaxRequestTarget target) {
    getFeedbackWindow().setContent(new FeedbackPanel("content"));
    getFeedbackWindow().show(target);
  }

  private boolean isParticipantExported(Participant participant) {
    return participantCaptureAndExportStrategy.isExported(participant.getBarcode());
  }

  private class ParticipantProvider extends SortableDataProviderEntityServiceImpl<Participant> {

    public ParticipantProvider() {
      super(queryService, Participant.class);
      setSort(new SortParam("lastName", true));
    }

    @Override
    protected List<Participant> getList(PagingClause paging, SortingClause... clauses) {
      return queryService.list(Participant.class, paging, clauses);
    }

    @Override
    public int size() {
      return queryService.count(Participant.class);
    }

  }

  private class ParticipantByInputFieldProvider extends SortableDataProviderEntityServiceImpl<Participant> {

    private String inputField;

    public ParticipantByInputFieldProvider(String inputField) {
      super(queryService, Participant.class);
      this.inputField = inputField;
      setSort(new SortParam("lastName", true));
    }

    @Override
    protected List<Participant> getList(PagingClause paging, SortingClause... clauses) {
      return participantService.getParticipantsByInputField(inputField, paging, clauses);
    }

    @Override
    public int size() {
      return participantService.countParticipantsByInputField(inputField);
    }

  }

  private class AppointedParticipantProvider extends SortableDataProviderEntityServiceImpl<Participant> {

    private Date from;

    private Date to;

    public AppointedParticipantProvider(Participant template) {
      super(queryService, Participant.class);
      setSort(new SortParam("appointment.date", true));

      Calendar cal = Calendar.getInstance();
      cal.setTime(new Date());
      cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
      this.from = cal.getTime();
      cal.add(Calendar.DAY_OF_MONTH, 1);
      this.to = cal.getTime();
    }

    @Override
    protected List<Participant> getList(PagingClause paging, SortingClause... clauses) {
      return participantService.getParticipants(from, to, paging, clauses);
    }

    @Override
    public int size() {
      return participantService.countParticipants(from, to);
    }

  }

  private class InterviewedParticipantProvider extends SortableDataProviderEntityServiceImpl<Participant> {

    public InterviewedParticipantProvider() {
      super(queryService, Participant.class);
      setSort(new SortParam("lastName", true));
    }

    @Override
    protected List<Participant> getList(PagingClause paging, SortingClause... clauses) {
      return participantService.getParticipants(InterviewStatus.IN_PROGRESS, paging, clauses);
    }

    @Override
    public int size() {
      return participantService.countParticipants(InterviewStatus.IN_PROGRESS);
    }

  }

  private class ParticipantListColumnProvider implements IColumnProvider<Participant>, Serializable {

    private static final long serialVersionUID = -9121583835357007L;

    private List<IColumn<Participant>> columns = new ArrayList<IColumn<Participant>>();

    private List<IColumn<Participant>> additional = new ArrayList<IColumn<Participant>>();

    public ParticipantListColumnProvider() {
      if(participantMetadata.getSupportedRecruitmentTypes().contains(RecruitmentType.ENROLLED)) {
        columns.add(new PropertyColumn<Participant>(new StringResourceModel("EnrollmentId", ParticipantSearchPage.this, null), "enrollmentId", "enrollmentId"));
      }
      columns.add(new PropertyColumn<Participant>(new StringResourceModel("ParticipantCode", ParticipantSearchPage.this, null), "barcode", "barcode"));
      columns.add(new PropertyColumn<Participant>(new StringResourceModel("LastName", ParticipantSearchPage.this, null), "lastName", "lastName"));
      columns.add(new PropertyColumn<Participant>(new StringResourceModel("FirstName", ParticipantSearchPage.this, null), "firstName", "firstName"));
      columns.add(new AbstractColumn<Participant>(new StringResourceModel("Appointment", ParticipantSearchPage.this, null), "appointment.date") {

        @Override
        public void populateItem(Item<ICellPopulator<Participant>> cellItem, String componentId, IModel<Participant> rowModel) {
          cellItem.add(new Label(componentId, DateModelUtils.getDateTimeModel(new PropertyModel<DateFormat>(ParticipantListColumnProvider.this, "dateTimeFormat"), new PropertyModel<Date>(rowModel, "appointment.date"))));
        }

      });

      columns.add(new AbstractColumn<Participant>(new StringResourceModel("Status", ParticipantSearchPage.this, null)) {

        public void populateItem(Item<ICellPopulator<Participant>> cellItem, String componentId, IModel<Participant> rowModel) {
          cellItem.add(new InterviewStatusFragment(componentId, rowModel));
        }

      });

      columns.add(new AbstractColumn<Participant>(new StringResourceModel("Actions", ParticipantSearchPage.this, null)) {

        public void populateItem(Item<ICellPopulator<Participant>> cellItem, String componentId, IModel<Participant> rowModel) {
          cellItem.add(new ActionListFragment(componentId, rowModel));
        }

      });

      columns.add(new AbstractColumn<Participant>(new Model<String>("")) {

        public void populateItem(Item<ICellPopulator<Participant>> cellItem, String componentId, IModel<Participant> rowModel) {
          cellItem.add(new LockedInterviewFragment(componentId, rowModel));
        }

      });

    }

    public List<IColumn<Participant>> getAdditionalColumns() {
      return additional;
    }

    public List<String> getColumnHeaderNames() {
      return null;
    }

    public List<IColumn<Participant>> getDefaultColumns() {
      return columns;
    }

    public List<IColumn<Participant>> getRequiredColumns() {
      return columns;
    }

    @SuppressWarnings("unused")
    public DateFormat getDateFormat() {
      return userSessionService.getDateFormat();
    }

    @SuppressWarnings("unused")
    public DateFormat getDateTimeFormat() {
      return userSessionService.getDateTimeFormat();
    }
  }

  @AuthorizeActions(actions = { @AuthorizeAction(action = Action.RENDER, roles = { "PARTICIPANT_MANAGER", "PARTICIPANT_RECEPTIONIST" }) })
  private class ActionFragment extends Fragment {

    private static final long serialVersionUID = 1L;

    @SpringBean
    private OnyxDataExport onyxDataExport;

    public ActionFragment(String id) {
      super(id, "actionFragment", ParticipantSearchPage.this);

      RepeatingView view = new RepeatingView("link");

      AjaxLink<Void> volunteerLink = new AjaxLink<Void>(view.newChildId()) {
        private static final long serialVersionUID = 1L;

        public void onClick(AjaxRequestTarget target) {
          Participant volunteer = participantFactory.createWithDefaultEssentialAttributes();
          volunteer.setRecruitmentType(RecruitmentType.VOLUNTEER);
          setResponsePage(new ParticipantReceptionPage(new Model<Participant>(volunteer), ParticipantSearchPage.this));
        }

        @Override
        public boolean isVisible() {
          return participantMetadata.getSupportedRecruitmentTypes().contains(RecruitmentType.VOLUNTEER);
        }
      };
      volunteerLink.add(new Label("label", new ResourceModel("EnrollVolunteer")));
      view.add(volunteerLink);

      AjaxLink<Void> updateParticipantsLink = new AjaxLink<Void>(view.newChildId()) {
        private static final long serialVersionUID = 1L;

        public void onClick(AjaxRequestTarget target) {
          updateParticipantListWindow.showConfirmation();
          updateParticipantListWindow.show(target);

          target.addComponent(updateParticipantListWindow.get("content"));
        }

        @Override
        public boolean isVisible() {
          return participantMetadata.getSupportedRecruitmentTypes().contains(RecruitmentType.ENROLLED) && participantMetadata.getUpdateAppointmentListEnabled();
        }
      };
      updateParticipantsLink.add(new Label("label", new ResourceModel("UpdateParticipantsList")));
      view.add(updateParticipantsLink);

      final Dialog participantRegistryDialog = DialogBuilder.buildDialog("participant-registry-dialog", new ResourceModel("ParticipantRegistryDialogTitle"), participantRegistryPanel = new ParticipantRegistryPanel("content")).setOptions(Option.CANCEL_OPTION).getDialog();
      participantRegistryDialog.setInitialHeight(36);
      participantRegistryDialog.setHeightUnit("em");

      participantRegistryDialog.setWindowClosedCallback(new WindowClosedCallback() {

        public void onClose(AjaxRequestTarget target, Status status) {
          if(status.equals(Status.SUCCESS)) {
            Participant participant = participantRegistryPanel.getLastLookedUpParticipant();
            participant.setSiteNo(applicationConfigurationService.getApplicationConfiguration().getSiteNo());
            participant.setAppointment(new Appointment(participant, new Date()));
            try {
              participantService.updateParticipant(participant);
              setResponsePage(new ParticipantReceptionPage(new DetachableEntityModel<Participant>(queryService, participant), ParticipantSearchPage.this));
            } catch(RuntimeException e) {
              StringResourceModel errorMessage = new StringResourceModel("UnableToSaveParticipant", ParticipantSearchPage.this, null);
              error(errorMessage.getString());
              getFeedbackWindow().setContent(new FeedbackPanel("content"));
              getFeedbackWindow().show(target);
            }
          }
        }
      });

      final IBehavior disableRegisterLinkButtonBehaviour = new ExecuteJavaScriptBehaviour("$('[name=registerLink]').attr('disabled','true');$('[name=registerLink]').css('color','rgba(0, 0, 0, 0.2)');$('[name=registerLink]').css('border-color','rgba(0, 0, 0, 0.2)');", true);

      final AjaxButton registerLink;
      participantRegistryDialog.addSubmitOption("RegisterLink", OptionSide.RIGHT, registerLink = new AjaxButton(view.newChildId()) {
        private static final long serialVersionUID = 1L;

        @Override
        protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
          participantRegistryDialog.close(target);
          participantRegistryDialog.setStatus(Status.SUCCESS);
        }

      }, "registerLink");
      registerLink.setOutputMarkupId(true);
      registerLink.add(disableRegisterLinkButtonBehaviour);

      final AjaxButton lookUpLink;
      participantRegistryDialog.addSubmitOption("Lookup", OptionSide.LEFT, lookUpLink = new AjaxButton(view.newChildId()) {
        private static final long serialVersionUID = 1L;

        @Override
        protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
          try {
            participantRegistryPanel.lookUpParticipant();
            registerLink.setEnabled(true);
            participantRegistryPanel.clearMessage();
          } catch(NoSuchParticipantException e) {
            participantRegistryPanel.reset();
            participantRegistryPanel.setMessage("NoParticipantFound");
            registerLink.setEnabled(false);
            registerLink.add(disableRegisterLinkButtonBehaviour);
          } catch(ParticipantRegistryLookupException e) {
            log.error("Participant registry lookup failed.", e);
            participantRegistryPanel.reset();
            participantRegistryPanel.setMessage("ParticipantLookupFailed");
            registerLink.setEnabled(false);
            registerLink.add(disableRegisterLinkButtonBehaviour);
          } finally {
            target.addComponent(participantRegistryPanel);
            target.addComponent(registerLink);
          }
        }

        @Override
        protected void onError(AjaxRequestTarget target, Form<?> form) {
          registerLink.setEnabled(false);
          registerLink.add(disableRegisterLinkButtonBehaviour);
          target.addComponent(registerLink);
        }

      }, "lookUpLink");
      participantRegistryDialog.getForm().setDefaultButton(lookUpLink);

      add(participantRegistryDialog);

      AjaxLink<Void> registryLink = new AjaxLink<Void>(view.newChildId()) {
        private static final long serialVersionUID = 1L;

        public void onClick(AjaxRequestTarget target) {
          participantRegistryPanel.reset();
          target.addComponent(participantRegistryDialog);
          participantRegistryDialog.show(target);
          registerLink.setEnabled(false);
          registerLink.add(disableRegisterLinkButtonBehaviour);
          target.addComponent(registerLink);
        }

        @Override
        public boolean isVisible() {
          return participantMetadata.getParticipantRegistryEnabled();
        }
      };
      registryLink.add(new Label("label", new ResourceModel("ParticipantRegistry")));
      view.add(registryLink);

      AjaxLink<Void> exportLink = new AjaxLink<Void>(view.newChildId()) {
        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          MultiLineLabel label = new MultiLineLabel("content", new StringResourceModel("ConfirmExportMessage", new Model<ValueMap>(new ValueMap("directory=" + onyxDataExport.getOutputRootDirectory().getAbsolutePath()))));
          label.add(new AttributeModifier("class", true, new Model<String>("long-confirmation-dialog-content")));

          ConfirmationDialog confirmationDialog = createExportDialog();
          confirmationDialog.setContent(label);

          confirmationDialog.setYesButtonCallback(new OnYesCallback() {
            private static final long serialVersionUID = 1L;

            public void onYesButtonClicked(AjaxRequestTarget target) {
              try {
                onyxDataExport.exportInterviews();
              } catch(Exception e) {
                log.error("Error on data export.", e);
              }
            }
          });

          ActionFragment.this.replace(confirmationDialog);
          confirmationDialog.show(target);
        }

      };
      exportLink.add(new Label("label", new ResourceModel("Export")));
      MetaDataRoleAuthorizationStrategy.authorize(exportLink, RENDER, "PARTICIPANT_MANAGER");
      view.add(exportLink);

      add(new EmptyPanel("dialog").setOutputMarkupId(true));
      add(view);
    }

    private ConfirmationDialog createExportDialog() {
      ConfirmationDialog confirmationDialog = new ConfirmationDialog("dialog");
      confirmationDialog.setTitle(new ResourceModel("ConfirmExport"));
      confirmationDialog.setInitialHeight(15);
      return confirmationDialog;
    }

  }

  private class InterviewStatusFragment extends Fragment {

    private static final long serialVersionUID = 1L;

    private class StatusModel extends Model<String> {

      private static final long serialVersionUID = 1L;

      private final IModel<InterviewStatus> statusModel;

      private boolean isExported;

      public StatusModel(IModel<Participant> modelObject, String expression, boolean isExportedParticipant) {
        super();
        statusModel = new PropertyModel<InterviewStatus>(modelObject, expression);
        isExported = isExportedParticipant;
      }

      @Override
      public String getObject() {
        if(statusModel.getObject() != null) {
          if(!isExported) {
            return "obiba-state-" + statusModel.getObject().toString().toLowerCase().replace("_", "");
          } else {
            return "obiba-state-exported";
          }
        }
        return "";
      }
    }

    public InterviewStatusFragment(String id, IModel<Participant> participantModel) {
      super(id, "interviewStatus", ParticipantSearchPage.this, participantModel);

      Label statusLabel;
      boolean isExportedParticipant = isParticipantExported(participantModel.getObject());
      if(isExportedParticipant) {
        statusLabel = new Label("status", new StringResourceModel("ExportedInterview", ParticipantSearchPage.this, null));
      } else {
        statusLabel = new Label("status", new StringResourceModel("InterviewStatus.${status}", ParticipantSearchPage.this, new PropertyModel<Interview>(participantModel, "interview"), ""));
      }
      statusLabel.add(new AttributeAppender("class", new StatusModel(participantModel, "interview.status", isExportedParticipant), " "));
      add(statusLabel);

    }
  }

  private class LockedInterviewFragment extends Fragment {

    private static final long serialVersionUID = 1L;

    public LockedInterviewFragment(String id, IModel<Participant> participantModel) {
      super(id, "lockedInterview", ParticipantSearchPage.this, participantModel);
      setOutputMarkupId(true);
      ContextImage image = new ContextImage("lock", new Model<String>("icons/locked.png"));
      add(image);

      if(interviewManager.isInterviewAvailable((Participant) participantModel.getObject())) {
        image.setVisible(false);
      } else {
        // Display tooltip.
        String interviewer = interviewManager.getInterviewer((Participant) participantModel.getObject());
        StringResourceModel tooltipResource = new StringResourceModel("InterviewerHasLockOnInterview", ParticipantSearchPage.this, new Model<String>(interviewer), interviewer);
        add(new AttributeAppender("title", true, tooltipResource, " "));
        add(new DisplayTooltipBehaviour(getMarkupId(), "{positionLeft: true, left: -5}"));
      }
    }
  }

  /**
   * This fragment uses link visibility in order to hide/display links within the list of available links. It replaces
   * the previous use of AjaxLinkList which would add/not add a component to the list in order to hide/display it. The
   * problem with this approach is that when the model changes, a component that should now be displayed cannot since it
   * isn't present in the list.
   * @see ONYX-169
   */
  private class ActionListFragment extends Fragment {

    private abstract class ActionLink extends AjaxLink<Participant> {

      public ActionLink(String id, IModel<Participant> participantModel) {
        super(id, participantModel);
      }

    }

    public ActionListFragment(String id, IModel<Participant> participantModel) {
      super(id, "actionList", ParticipantSearchPage.this, participantModel);

      RepeatingView repeater = new RepeatingView("link");

      // View
      AjaxLink<Participant> link = new ActionLink(repeater.newChildId(), participantModel) {
        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          ParticipantPanel component = new ParticipantPanel("content", getModel());
          component.add(new AttributeModifier("class", true, new Model<String>("obiba-content participant-panel-content")));
          participantDetailsModalWindow.setContent(component);
          participantDetailsModalWindow.show(target);
        }
      };
      link.add(new Label("label", new ResourceModel("View")));
      repeater.add(link);

      // Interview
      final boolean interviewIsLocked = !interviewManager.isInterviewAvailable(getParticipant());
      link = new ActionLink(repeater.newChildId(), participantModel) {
        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          // Determine if the interview is locked after the user clicks the link. ONYX-664
          if(!interviewManager.isInterviewAvailable(getParticipant())) {
            content = new UnlockInterviewPanel(unlockInterviewWindow.getContentId(), getModel());
            content.add(new AttributeModifier("class", true, new Model<String>("obiba-content unlockInterview-panel-content")));
            unlockInterviewWindow.setContent(content);
            target.appendJavascript("Wicket.Window.unloadConfirmation = false;");

            if(userSessionService.getRoles().contains(Role.PARTICIPANT_MANAGER)) {
              unlockInterviewWindow.show(target);
            } else {
              error((new StringResourceModel("InterviewLocked", this, ActionListFragment.this.getDefaultModel())).getString());
              getFeedbackWindow().setContent(new FeedbackPanel("content"));
              getFeedbackWindow().show(target);
            }
          } else {
            interviewManager.obtainInterview(getParticipant());
            setResponsePage(InterviewPage.class);
          }
        }

        @Override
        public boolean isVisible() {
          // Visible when participant has been assigned a barcode and participant not exported.
          return getParticipant().getBarcode() != null && !isParticipantExported(getParticipant());
        }
      };
      link.add(new Label("label", new ResourceModel("Interview")));
      MetaDataRoleAuthorizationStrategy.authorize(link, RENDER, "PARTICIPANT_MANAGER,DATA_COLLECTION_OPERATOR");

      // Locked interviews can only be unlocked by a participant manager (ONYX-463)
      if(interviewIsLocked) {
        MetaDataRoleAuthorizationStrategy.authorize(link, RENDER, "PARTICIPANT_MANAGER");
      }

      repeater.add(link);

      // Receive
      link = new ActionLink(repeater.newChildId(), participantModel) {
        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          setResponsePage(new ParticipantReceptionPage(getModel(), ParticipantSearchPage.this));
        }

        @Override
        public boolean isVisible() {
          // Reception allowed when participant has no barcode associated
          return getParticipant().getBarcode() == null;
        }
      };
      link.add(new Label("label", new ResourceModel("Receive")));
      MetaDataRoleAuthorizationStrategy.authorize(link, RENDER, "PARTICIPANT_MANAGER,PARTICIPANT_RECEPTIONIST");
      repeater.add(link);

      // Edit
      link = new ActionLink(repeater.newChildId(), participantModel) {
        private static final long serialVersionUID = 1L;

        @Override
        public void onClick(AjaxRequestTarget target) {
          EditParticipantPanel component = new EditParticipantPanel("content", getModel(), ParticipantSearchPage.this, editParticipantDetailsModalWindow);
          component.add(new AttributeModifier("class", true, new Model<String>("obiba-content participant-panel-content")));
          editParticipantDetailsModalWindow.setContent(component);
          editParticipantDetailsModalWindow.show(target);
          target.appendJavascript("$('div.wicket-modal').css('overflow','visible');");
        }

        @Override
        public boolean isVisible() {
          // Visible if participant has been received and some attributes are editable. Also not visible if participant
          // has been exported.
          return getParticipant().getBarcode() != null && participantMetadata.hasEditableAfterReceptionAttribute() && !isParticipantExported(getParticipant());
        }
      };
      link.add(new Label("label", new ResourceModel("Edit")));
      MetaDataRoleAuthorizationStrategy.authorize(link, RENDER, "PARTICIPANT_MANAGER,PARTICIPANT_RECEPTIONIST");
      repeater.add(link);

      add(repeater);
    }

    Participant getParticipant() {
      return (Participant) getDefaultModelObject();
    }
  }

  private class ParticipantEntityList extends OnyxEntityList<Participant> {

    private static final long serialVersionUID = 1L;

    public ParticipantEntityList(String id, Class<Participant> type, IColumnProvider<Participant> columns, IModel<String> title) {
      super(id, queryService, type, columns, title);
    }

    public ParticipantEntityList(String id, Participant template, IColumnProvider<Participant> columns, IModel<String> title) {
      super(id, queryService, template, columns, title);
    }

    public ParticipantEntityList(String id, SortableDataProvider<Participant> dataProvider, IColumnProvider<Participant> columns, IModel<String> title) {
      super(id, dataProvider, columns, title);
    }

    @Override
    protected void onPageChanged() {
      IRequestTarget target = getRequestCycle().getRequestTarget();
      if(getRequestCycle().getRequestTarget() instanceof AjaxRequestTarget) {
        ((AjaxRequestTarget) target).appendJavascript("styleParticipantSearchNavigationBar();");
      }
      super.onPageChanged();
    }
  }

  @Override
  public void renderHead(IHeaderResponse response) {
    super.renderHead(response);
    response.renderOnLoadJavascript("styleParticipantSearchNavigationBar();");
  }

  @Override
  protected void onAfterRender() {
    super.onAfterRender();
    IRequestTarget target = getRequestCycle().getRequestTarget();
    if(getRequestCycle().getRequestTarget() instanceof AjaxRequestTarget) {
      ((AjaxRequestTarget) target).appendJavascript("styleParticipantSearchNavigationBar();");
    }
  }
}
