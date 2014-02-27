/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.core.service.impl;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.obiba.core.service.impl.PersistenceManagerAwareService;
import org.obiba.onyx.core.domain.participant.Interview;
import org.obiba.onyx.core.domain.participant.InterviewStatus;
import org.obiba.onyx.core.domain.participant.Participant;
import org.obiba.onyx.core.domain.user.User;
import org.obiba.onyx.core.service.InterviewManager;
import org.obiba.onyx.core.service.UserSessionService;
import org.obiba.onyx.engine.state.StageExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 
 */
public class DefaultInterviewManagerImpl extends PersistenceManagerAwareService implements InterviewManager {

  private static final Logger log = LoggerFactory.getLogger(DefaultInterviewManagerImpl.class);

  private final List<InterviewLock> interviewLocks = Collections.synchronizedList(new LinkedList<InterviewLock>());

  private final Map<Serializable, Map<String, StageExecutionContext>> interviewStageContextsMap = new HashMap<Serializable, Map<String, StageExecutionContext>>();

  private UserSessionService userSessionService;

  public DefaultInterviewManagerImpl() {
    interviewStageContextsMap.size();
  }

  public void setUserSessionService(UserSessionService userSessionService) {
    this.userSessionService = userSessionService;
  }

  @Override
  synchronized public Participant getInterviewedParticipant() {
    InterviewLock lock = findLock(userSessionService.getSessionId());
    if(lock != null) {
      return lock.getParticipant();
    }
    throw new NoSuchInterviewException("No current interview");
  }

  @Override
  synchronized public Map<String, StageExecutionContext> getStageContexts() {
    Participant participant = getInterviewedParticipant();
    if(participant != null) {
      Map<String, StageExecutionContext> contexts = interviewStageContextsMap.get(participant.getId());
      if(contexts == null) {
        contexts = new HashMap<String, StageExecutionContext>();
        interviewStageContextsMap.put(participant.getId(), contexts);
      }
      return contexts;
    }
    throw new NoSuchInterviewException("No current interview");
  }

  @Override
  synchronized public String getInterviewer(Participant participant) {
    InterviewLock lock = findLock(participant);
    if(lock != null) {
      return lock.getOperatorId();
    }
    return null;
  }

  @Override
  synchronized public boolean isInterviewAvailable(Participant participant) {
    return findLock(participant) == null;
  }

  @Override
  synchronized public Interview obtainInterview(Participant participant) {
    if(isInterviewAvailable(participant)) {

      lockInterview(participant);

      Interview interview = participant.getInterview();
      if(interview == null) {
        interview = new Interview();
        interview.setParticipant(participant);
        interview.setStartDate(new Date());
        interview.setStatus(InterviewStatus.IN_PROGRESS);
        getPersistenceManager().save(interview);
        getPersistenceManager().refresh(participant);
      }

      return participant.getInterview();
    }
    throw new IllegalStateException("Cannot obtain interview. Interview is locked.");
  }

  @Override
  synchronized public Interview overrideInterview(Participant participant) {
    unlockInterview(findLock(participant));
    lockInterview(participant);
    return participant.getInterview();
  }

  @Override
  synchronized public void releaseInterview() {
    releaseInterview(userSessionService.getSessionId());
  }

  // Ensures an active transaction when invoked outside request cycle
  // ONYX-1622
  @Override
  @Transactional
  synchronized public void releaseInterview(String sessionId) {
    unlockInterview(findLock(sessionId));
  }

  protected void unlockInterview(InterviewLock lock) {
    if(lock != null) {
      log.info("User {} has locked interview during {}s for participant id={}.", lock.getOperatorId(), Math.round(
          (System.currentTimeMillis() - lock.getTimeLock()) / 1000),
          lock.getParticipant().getId());
      this.interviewLocks.remove(lock);

      Interview interview = lock.getParticipant().getInterview();
      if(interview.getStatus() == InterviewStatus.IN_PROGRESS) {
        updateInterviewDuration(interview, System.currentTimeMillis() - lock.getTimeLock());
      }
    }
  }

  protected InterviewLock lockInterview(Participant participant) {
    InterviewLock lock = new InterviewLock(participant);
    interviewLocks.add(lock);
    log.info("User {} has locked interview for participant id={}.", userSessionService.getUserName(), participant.getId());
    return lock;
  }

  protected InterviewLock findLock(Participant participant) {
    for(InterviewLock lock : this.interviewLocks) {
      if(lock.isForParticipant(participant)) {
        return lock;
      }
    }
    return null;
  }

  protected InterviewLock findLock(String sessionId) {
    for(InterviewLock lock : this.interviewLocks) {
      if(lock.isForSession(sessionId)) {
        return lock;
      }
    }
    return null;
  }

  private void updateInterviewDuration(Interview interview, long timeLockedMillis) {
    int timeLockedSeconds = (int) (timeLockedMillis / 1000l);

    interview.incrementDuration(timeLockedSeconds);
    getPersistenceManager().save(interview);
  }

  public class InterviewLock {

    private final Serializable participantId;

    private final String operatorSessionId;

    private final String operatorId;

    private final long timeLock;

    InterviewLock(Participant participant) {
      this.participantId = participant.getId();
      this.operatorSessionId = userSessionService.getSessionId();
      this.operatorId = userSessionService.getUserName();
      this.timeLock = System.currentTimeMillis();
    }

    public long getTimeLock() {
      return timeLock;
    }

    public Participant getParticipant() {
      return getPersistenceManager().get(Participant.class, participantId);
    }

    public boolean isForParticipant(Participant participant) {
      return participantId.equals(participant.getId());
    }

    public String getSessionId() {
      return operatorSessionId;
    }

    public boolean isForSession(String sessionId) {
      return operatorSessionId.equals(sessionId);
    }

    public String getOperatorId() {
      return operatorId;
    }

    public boolean isForOperator(User operator) {
      return operatorId.equals(operator.getId());
    }

    public String toString() {
      return getOperatorId() + "(" + getSessionId() + ")->" + getParticipant().getBarcode();
    }

  }

}
