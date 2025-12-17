package com.kingpixel.ultraeconomy.web.server;


import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.web.server.api.PlayersApiServlet;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import java.util.EnumSet;

public class WebServer {

  private final Server server;

  public WebServer(int port) {
    this.server = new Server(port);
  }

  public void start() {
    try {
      ServletContextHandler context = new ServletContextHandler();
      context.setContextPath("/");

      // API
      context.addServlet(PlayersApiServlet.class, "/api/players");

      // CORS
      if (UltraEconomy.config.isDebug()) {
        FilterHolder cors = context.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

        // Permitir todos los orígenes en desarrollo
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,PUT,DELETE,OPTIONS");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
      }

      // Web estática
      context.setResourceBase(
        getClass().getClassLoader()
          .getResource("web")
          .toExternalForm()
      );
      context.addServlet(DefaultServlet.class, "/");
      

      server.setHandler(context);
      server.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
