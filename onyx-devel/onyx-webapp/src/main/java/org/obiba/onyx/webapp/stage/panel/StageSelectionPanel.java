/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.webapp.stage.panel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.obiba.onyx.core.domain.participant.InterviewStatus;
import org.obiba.onyx.core.service.ActiveInterviewService;
import org.obiba.onyx.core.service.UserSessionService;
import org.obiba.onyx.engine.Action;
import org.obiba.onyx.engine.ModuleRegistry;
import org.obiba.onyx.engine.Stage;
import org.obiba.onyx.engine.state.IStageExecution;
import org.obiba.onyx.util.DateUtil;
import org.obiba.onyx.webapp.action.panel.ActionsPanel;
import org.obiba.onyx.webapp.stage.page.StagePage;
import org.obiba.onyx.wicket.StageModel;
import org.obiba.onyx.wicket.action.ActionWindow;
import org.obiba.onyx.wicket.panel.OnyxEntityList;
import org.obiba.onyx.wicket.reusable.FeedbackWindow;
import org.obiba.wicket.markup.html.table.IColumnProvider;
import org.obiba.wicket.model.MessageSourceResolvableStringModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StageSelectionPanel extends Panel {

  private static final long serialVersionUID = 6282742572162384139L;

  private static final Logger log = LoggerFactory.getLogger(StageSelectionPanel.class);

  @SpringBean(name = "activeInterviewService")
  private ActiveInterviewService activeInterviewService;

  @SpringBean(name = "userSessionService")
  private UserSessionService userSessionService;

  @SpringBean
  private ModuleRegistry moduleRegistry;

  private ActionWindow modal;

  private OnyxEntityList<Stage> list;

  private FeedbackPanel feedbackPanel;

  private FeedbackWindow feedbackWindow;

  @SuppressWarnings("serial")
  public StageSelectionPanel(String id, FeedbackWindow feedbackWindow) {
    super(id);
    setOutputMarkupId(true);

    this.feedbackWindow = feedbackWindow;
    this.feedbackPanel = new FeedbackPanel("content");

    checkStageStatus();

    add(modal = new ActionWindow("modal") {

      @Override
      public void onActionPerformed(AjaxRequestTarget target, Stage stage, Action action) {
        IStageExecution exec = activeInterviewService.getStageExecution(stage);
        if(!exec.isInteractive()) {
          checkStageStatus();

          if(((List<FeedbackMessage>) StageSelectionPanel.this.feedbackPanel.getFeedbackMessagesModel().getObject()).size() > 0) {
            StageSelectionPanel.this.feedbackWindow.setContent(StageSelectionPanel.this.feedbackPanel);
            StageSelectionPanel.this.feedbackWindow.show(target);
          }

          target.addComponent(list);
          StageSelectionPanel.this.onActionPerformed(target, stage, action);
        } else {
          log.debug("GOTO stage {}/{}", stage.getName(), action.getDateTime());
          setResponsePage(new StagePage(new StageModel(moduleRegistry, stage)));
        }
      }

    });

    add(list = new OnyxEntityList<Stage>("list", new StageProvider(), new StageListColumnProvider(), new StringResourceModel("StageList", StageSelectionPanel.this, null)));
  }

  private void checkStageStatus() {
    Stage interactiveStage = activeInterviewService.getInteractiveStage();
    if(interactiveStage != null) {
      log.warn("Wrong status for stage {}", interactiveStage.getName());
    }
  }

  abstract public void onViewComments(AjaxRequestTarget target, String stage);

  abstract public void onViewLogs(AjaxRequestTarget target, String stage, boolean commentsOnly);

  abstract public void onActionPerformed(AjaxRequestTarget target, Stage stage, Action action);

  private class StageProvider extends SortableDataProvider<Stage> {

    private static final long serialVersionUID = 6022606267778864539L;

    public StageProvider() {
    }

    public Iterator<Stage> iterator(int first, int count) {
      List<Stage> stages = moduleRegistry.listStages();
      return stages.iterator();
    }

    public IModel<Stage> model(Stage object) {
      return new StageModel(moduleRegistry, object);
    }

    public int size() {
      return moduleRegistry.listStages().size();
    }

  }

  private class StageListColumnProvider implements IColumnProvider<Stage>, Serializable {

    private static final long serialVersionUID = -9121583835345457007L;

    private List<IColumn<Stage>> columns = new ArrayList<IColumn<Stage>>();

    private List<IColumn<Stage>> additional = new ArrayList<IColumn<Stage>>();

    @SuppressWarnings("serial")
    public StageListColumnProvider() {

      columns.add(new AbstractColumn<Stage>(new Model<String>("#")) {
        @Override
        public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
          // cellItem represents the cell (td). It's index is the column's index.
          // To obtain the row's index, we must find the cell's parent Item class.
          // See
          // http://www.nabble.com/getIndex()-and-getCurrentPage()-in-Abstractcolumn-in-DataTabel-td20171197.html#a20171197
          // and http://www.nabble.com/Item.getIndex%28%29-on-DefaultDataTable-tp17676006p17676006.html
          Item row = ((Item) cellItem.findParent(Item.class));
          // The Index is zero-based
          cellItem.add(new Label(componentId, ((Integer) (row.getIndex() + 1)).toString() + " -"));
        }
      });

      columns.add(new AbstractColumn<Stage>(new ResourceModel("Name")) {

        @Override
        public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
          cellItem.add(new Label(componentId, new MessageSourceResolvableStringModel(new PropertyModel<String>(rowModel, "description"))));
        }

      });

      columns.add(new AbstractColumn<Stage>(new ResourceModel("Status")) {

        @Override
        public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
          cellItem.add(new StageStatusFragment(componentId, rowModel));
        }

      });

      columns.add(new AbstractColumn<Stage>(new ResourceModel("Start")) {

        @Override
        public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
          cellItem.add(new Label(componentId, new StartTimeModel(rowModel)));
        }

      });

      columns.add(new AbstractColumn<Stage>(new ResourceModel("End")) {

        @Override
        public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
          cellItem.add(new Label(componentId, new EndTimeModel(rowModel)));
        }

      });

      if(activeInterviewService.getInterview().getStatus().equals(InterviewStatus.IN_PROGRESS)) {
        columns.add(new AbstractColumn<Stage>(new ResourceModel("Actions")) {

          @Override
          public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
            cellItem.add(new ActionsPanel(componentId, rowModel, modal));
          }

        });
      }

      columns.add(new AbstractColumn<Stage>(new ResourceModel("Log")) {

        List<Action> interviewComments;

        List<Action> interviewActions;

        @Override
        public void populateItem(Item<ICellPopulator<Stage>> cellItem, String componentId, IModel<Stage> rowModel) {
          Stage stage = (Stage) rowModel.getObject();

          final String stageName = stage.getName();

          // Show link only if there are existing log entries for the selected Stage
          if(logEntryExistsForStage(stageName)) {
            cellItem.add(new ViewCommentsActionPanel(componentId) {

              private static final long serialVersionUID = 1L;

              @Override
              public void onViewComments(AjaxRequestTarget target) {
                StageSelectionPanel.this.onViewLogs(target, stageName, true);
              }

              @Override
              public void onViewLogs(AjaxRequestTarget target) {
                StageSelectionPanel.this.onViewLogs(target, stageName, false);
              }

              @Override
              public boolean commentEntryExists() {
                return logCommentExistsForStage(stageName);
              }

              @Override
              public boolean logEntryExists() {
                return logEntryExistsForStage(stageName);
              }

            });

          } else {
            cellItem.add(new Label(componentId, ""));
          }
        }

        private boolean logEntryExistsForStage(String stageName) {
          if(interviewActions == null) {
            interviewActions = activeInterviewService.getInterviewActions();
          }

          for(Action action : interviewActions) {
            if(action.getStage() != null && action.getStage().equals(stageName)) {
              return true;
            }
          }
          return false;
        }

        private boolean logCommentExistsForStage(String stageName) {
          if(interviewComments == null) {
            interviewComments = activeInterviewService.getInterviewComments();
          }

          for(Action action : interviewComments) {
            if(action.getStage() != null && action.getStage().equals(stageName)) {
              return true;
            }
          }
          return false;
        }

      });

    }

    public List<IColumn<Stage>> getAdditionalColumns() {
      return additional;
    }

    public List<String> getColumnHeaderNames() {
      return null;
    }

    public List<IColumn<Stage>> getDefaultColumns() {
      return columns;
    }

    public List<IColumn<Stage>> getRequiredColumns() {
      return columns;
    }

  }

  private class StageStatusFragment extends Fragment {

    private static final long serialVersionUID = 1L;

    /**
     * @param id
     * @param model
     */
    public StageStatusFragment(String id, IModel<Stage> model) {
      super(id, "stageStatusFragment", StageSelectionPanel.this, model);
      Label statusLabel = new Label("status", new MessageSourceResolvableStringModel(new PropertyModel<String>(this, "stageExecution.message")));
      add(statusLabel);
      addCssClasses();

      add(new Label("statusReason", new MessageSourceResolvableStringModel(new PropertyModel<String>(this, "stageExecution.reasonMessage"))) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isVisible() {
          return getStageExecution().getReasonMessage() != null;
        }
      });
      get("status").setRenderBodyOnly(true);
      get("statusReason").setRenderBodyOnly(true);
    }

    public IStageExecution getStageExecution() {
      Stage stage = (Stage) getDefaultModelObject();
      return activeInterviewService.getStageExecution(stage);
    }

    @SuppressWarnings("unused")
    public String getStageState() {
      IStageExecution stageExec = getStageExecution();
      return "obiba-state-" + stageExec.getMessage().getDefaultMessage().toLowerCase();
    }

    private void addCssClasses() {
      // Add the generic obiba-state class.
      add(new AttributeAppender("class", new Model<String>("obiba-state"), " "));

      // Add the specific obiba-state-* class based on the state execution's state.
      add(new AttributeAppender("class", new PropertyModel<String>(this, "stageState"), " "));
    }
  }

  private class StartTimeModel extends Model<String> {

    private static final long serialVersionUID = 1L;

    private IModel<Stage> stageModel;

    public StartTimeModel(IModel<Stage> stageModel) {
      this.stageModel = stageModel;
    }

    public String getObject() {
      Stage stage = stageModel.getObject();
      IStageExecution stageExec = activeInterviewService.getStageExecution(stage);
      Date startTime = stageExec.getStartTime();

      String formattedStartTime = "";

      if(startTime != null) {
        formattedStartTime = userSessionService.getTimeFormat().format(startTime);
      }

      return formattedStartTime;
    }
  }

  private class EndTimeModel extends Model<String> {

    private static final long serialVersionUID = 1L;

    private IModel<Stage> stageModel;

    public EndTimeModel(IModel<Stage> stageModel) {
      this.stageModel = stageModel;
    }

    public String getObject() {
      Stage stage = (Stage) stageModel.getObject();
      IStageExecution stageExec = activeInterviewService.getStageExecution(stage);
      Date startTime = stageExec.getStartTime();
      Date endTime = stageExec.getEndTime();

      String formattedEndTime = "";

      if(endTime != null) {
        int daysBetween = DateUtil.getDaysBetween(startTime, endTime);

        formattedEndTime = userSessionService.getTimeFormat().format(endTime);
        if(daysBetween != 0) {
          formattedEndTime += " +" + daysBetween;
        }
      }

      return formattedEndTime;
    }
  }
}
