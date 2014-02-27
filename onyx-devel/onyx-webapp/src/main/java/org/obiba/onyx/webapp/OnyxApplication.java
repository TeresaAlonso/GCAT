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

import java.util.Map;

import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.Request;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.Response;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.Session;
import org.apache.wicket.authorization.IUnauthorizedComponentInstantiationListener;
import org.apache.wicket.authorization.UnauthorizedInstantiationException;
import org.apache.wicket.authorization.strategies.role.RoleAuthorizationStrategy;
import org.apache.wicket.markup.html.SecurePackageResourceGuard;
import org.apache.wicket.markup.html.pages.AccessDeniedPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.request.target.coding.QueryStringUrlCodingStrategy;
import org.apache.wicket.spring.ISpringContextLocator;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.lang.PackageName;
import org.apache.wicket.util.time.Duration;
import org.obiba.onyx.core.domain.user.User;
import org.obiba.onyx.core.service.UserService;
import org.obiba.onyx.webapp.authentication.UserRolesAuthorizer;
import org.obiba.onyx.webapp.config.page.ApplicationConfigurationPage;
import org.obiba.onyx.webapp.home.page.HomePage;
import org.obiba.onyx.webapp.home.page.InternalErrorPage;
import org.obiba.onyx.webapp.login.page.LoginPage;
import org.obiba.onyx.webapp.participant.page.ParticipantSearchPage;
import org.obiba.onyx.webapp.stage.page.StagePage;
import org.obiba.onyx.webapp.workstation.page.WorkstationPage;
import org.obiba.onyx.webapp.ws.ExportWebService;
import org.obiba.onyx.webapp.ws.PurgeWebService;
import org.obiba.runtime.Version;
import org.obiba.wicket.application.ISpringWebApplication;
import org.obiba.wicket.application.WebApplicationStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

public class OnyxApplication extends WebApplication implements ISpringWebApplication, IUnauthorizedComponentInstantiationListener {

  private final Logger log = LoggerFactory.getLogger(OnyxApplication.class);

  /**
   * Singleton instance of spring application context locator
   */
  private final static ISpringContextLocator contextLocator = new ISpringContextLocator() {

    private static final long serialVersionUID = 1L;

    public ApplicationContext getSpringContext() {
      Application app = Application.get();
      return ((OnyxApplication) app).internalGetApplicationContext();
    }
  };

  private XmlWebApplicationContext applicationContext;

  private UserService userService;

  private OnyxApplicationConfiguration onyxApplicationConfiguration;

  public UserService getUserService() {
    return userService;
  }

  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  public void setOnyxApplicationConfiguration(OnyxApplicationConfiguration onyxApplicationConfiguration) {
    this.onyxApplicationConfiguration = onyxApplicationConfiguration;
  }

  public Version getVersion() {
    return onyxApplicationConfiguration.getVersion();
  }

  /**
   * Get the configured type or falls back to wicket style configuration.
   * @see OnyxApplicationConfiguration
   */
  @Override
  public String getConfigurationType() {
    if(onyxApplicationConfiguration != null && onyxApplicationConfiguration.getConfigurationType() != null) {
      return onyxApplicationConfiguration.getConfigurationType();
    }
    return super.getConfigurationType();
  }

  @Override
  public Session newSession(Request request, Response response) {
    return new OnyxAuthenticatedSession(this, request);
  }

  public void onUnauthorizedInstantiation(Component component) {
    // If there is a sign in page class declared, and the unauthorized component is a page, but it's not the sign in
    // page
    if(component instanceof Page) {
      if(!OnyxAuthenticatedSession.get().isSignedIn()) {
        // Redirect to intercept page to let the user sign in
        throw new RestartResponseAtInterceptPageException(LoginPage.class);
      }
      // User is signed in but doesn't have the proper access rights. Display error and redirect accordingly.
      throw new RestartResponseAtInterceptPageException(AccessDeniedPage.class);

    }
    // The component was not a page, so show an error message in the FeedbackPanel of the page
    component.error("You do not have sufficient privileges to see this component.");
    throw new UnauthorizedInstantiationException(component.getClass());
  }

  public ISpringContextLocator getSpringContextLocator() {
    return contextLocator;
  }

  @Override
  public Class<? extends Page> getHomePage() {
    User template = new User();
    template.setDeleted(false);

    if(userService.getUserCount(template) > 0) {
      if(OnyxAuthenticatedSession.get().isSignedIn()) {
        return HomePage.class;
      }
      return LoginPage.class;

    }
    return ApplicationConfigurationPage.class;

  }

  public boolean isDevelopmentMode() {
    return Application.DEVELOPMENT.equalsIgnoreCase(getConfigurationType());
  }

  protected final ApplicationContext internalGetApplicationContext() {
    return applicationContext;
  }

  protected void createApplicationContext() {
    try {
      OnyxApplicationPropertyPlaceholderConfigurer configurer = new OnyxApplicationPropertyPlaceholderConfigurer(getServletContext());

      applicationContext = new XmlWebApplicationContext();
      applicationContext.setServletContext(getServletContext());
      applicationContext.addBeanFactoryPostProcessor(configurer);
      applicationContext.setConfigLocation(
          "WEB-INF/spring/context.xml," + configurer.getConfigPath() + "/*/module-context.xml");
      applicationContext.refresh();

      // Required for WebApplicationContext
      getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);

      // Inject our dependencies
      applicationContext.getAutowireCapableBeanFactory().autowireBeanProperties(this,
          AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false);

      // Call the configure() method again because we depend on the ApplicationContext for certain
      // configuration settings (specifically, the Deployment mode)
      // This method is called by Application#internalInit. Since we shouldn't override that method
      // we must call configure twice. It works for Wicket 1.3.5, this may break something in the future.
      configure();
    } catch(Exception e) {
      throw new RuntimeException(e);
    }

  }

  protected void init() {
    log.info("Onyx Web Application [{}] is starting", getServletContext().getContextPath());
    super.init();

    createApplicationContext();

    super.addComponentInstantiationListener(new SpringComponentInjector(this, applicationContext, true));

    forEachListeners(new IListenerCallback() {
      public void handleListener(String beanName, WebApplicationStartupListener listener) {
        listener.startup(OnyxApplication.this);
      }

      public boolean terminateOnException() {
        return true;
      }
    });

    // nice urls
    mount("main", PackageName.forClass(HomePage.class));
    mount("participants", PackageName.forClass(ParticipantSearchPage.class));
    mount("workstation", PackageName.forClass(WorkstationPage.class));
    mount("stage", PackageName.forClass(StagePage.class));
    mount(new QueryStringUrlCodingStrategy("/ws/purge", PurgeWebService.class));
    mount(new QueryStringUrlCodingStrategy("/ws/export", ExportWebService.class));
    // mount("administration", PackageName.forClass(AdministrationPage.class));

    getSecuritySettings().setAuthorizationStrategy(new RoleAuthorizationStrategy(new UserRolesAuthorizer()));
    getSecuritySettings().setUnauthorizedComponentInstantiationListener(this);

    getApplicationSettings().setPageExpiredErrorPage(HomePage.class);
    getApplicationSettings().setInternalErrorPage(InternalErrorPage.class);

    SecurePackageResourceGuard guard = new SecurePackageResourceGuard();
    guard.addPattern("+*.*");
    getResourceSettings().setPackageResourceGuard(guard);
    
    // Set default timeout
    getRequestCycleSettings().setTimeout(Duration.ONE_HOUR);

    log.info("Onyx Web Application [{}] v{} has started", getServletContext().getContextPath(), this.getVersion());
  }

  @Override
  protected void onDestroy() {
    log.info("Onyx Web Application [{}] is stopping", getServletContext().getContextPath());
    forEachListeners(new IListenerCallback() {
      public void handleListener(String beanName, WebApplicationStartupListener listener) {
        listener.shutdown(OnyxApplication.this);
      }

      public boolean terminateOnException() {
        return false;
      }
    });
    log.info("Destroying Spring ApplicationContext");
    applicationContext.destroy();
    log.info("Destroying Web Application sessions");
    getSessionStore().destroy();
    log.info("Onyx Web Application [{}] has been stopped", getServletContext().getContextPath());
    super.onDestroy();
  }

  /**
   * Finds implementations of {@link WebApplicationStartupListener} in Spring's Application Context and executes the
   * specified callback for each instance found.
   * @param callback the callback implementation to call for each listener instance
   */
  private void forEachListeners(IListenerCallback callback) {
    Map<String, WebApplicationStartupListener> listeners = applicationContext.getBeansOfType(
        WebApplicationStartupListener.class);
    if(listeners != null) {
      for(Map.Entry<String, WebApplicationStartupListener> entry : listeners.entrySet()) {
        log.info("Executing WebApplicationStartupListener named {} of type {}", entry.getKey(), entry.getValue().getClass().getSimpleName());
        try {
          callback.handleListener(entry.getKey(), entry.getValue());
        } catch(RuntimeException e) {
          log.error("Error executing WebApplicationStartupListener named {} of type {}", entry.getKey(), entry.getValue().getClass().getSimpleName());
          log.error("Reported error : ", e);
          if(callback.terminateOnException()) throw e;
        }
      }
    }
  }

  /**
   * Called for each WebApplicationStartupListener found in Spring's application context. Implementations should either
   * call startup or shutdown.
   */
  private interface IListenerCallback {
    public void handleListener(String beanName, WebApplicationStartupListener listener);

    public boolean terminateOnException();
  }

  @Override
  public RequestCycle newRequestCycle(final Request request, final Response response) {
    return new OnyxRequestCycle(this, (WebRequest) request, (WebResponse) response);
  }

}
