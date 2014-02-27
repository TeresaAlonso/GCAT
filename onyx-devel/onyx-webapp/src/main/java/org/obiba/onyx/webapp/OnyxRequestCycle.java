/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.webapp;

import org.apache.wicket.Page;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.model.IModel;
import org.apache.wicket.protocol.http.PageExpiredException;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebRequestCycle;
import org.mozilla.javascript.EcmaError;
import org.obiba.magma.MagmaRuntimeException;
import org.obiba.onyx.core.exception.ExceptionUtils;
import org.obiba.onyx.core.service.impl.NoSuchInterviewException;
import org.obiba.onyx.engine.Stage;
import org.obiba.onyx.webapp.home.page.InternalErrorPage;
import org.obiba.onyx.webapp.login.page.LoginPage;
import org.obiba.onyx.webapp.stage.page.InternalErrorStagePage;
import org.obiba.onyx.webapp.stage.page.StagePage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * implements an alternative way of managing the runtimeExceptions
 */
class OnyxRequestCycle extends WebRequestCycle {

  private final Logger log = LoggerFactory.getLogger(OnyxRequestCycle.class);

  /**
   * @param application
   * @param request
   * @param response
   */
  OnyxRequestCycle(WebApplication application, WebRequest request, Response response) {
    super(application, request, response);
  }

  @Override
  public Page onRuntimeException(Page page, RuntimeException e) {
    if(e instanceof PageExpiredException) {
      return new LoginPage();
    }

    Throwable t = e;
    log.info("Request cycle runtime exception", e);

    while(true) {
      if(t instanceof NoSuchInterviewException) {
        return newErrorPage(page, "No current interview");
      } else if(t instanceof MagmaRuntimeException || t instanceof EcmaError) {
        return newErrorPage(page, t.getMessage());
      }

      if(t.getCause() != null) {
        t = t.getCause();
      } else {
        break;
      }
    }

    return newErrorPage(page, e.getMessage());
  }

  @SuppressWarnings("unchecked")
  private Page newErrorPage(Page page, String... messages) {
    Session.get().cleanupFeedbackMessages();
    for(String msg : messages) {
      if(msg != null) {
        Session.get().error(ExceptionUtils.cleanMessage(msg));
      }
    }
    Session.get().dirty();

    Page rpage;
    if(page instanceof StagePage) {
      rpage = new InternalErrorStagePage((StagePage) page, (IModel<Stage>) page.getDefaultModel());
    } else {
      rpage = new InternalErrorPage();
    }

    return rpage;
  }
}
