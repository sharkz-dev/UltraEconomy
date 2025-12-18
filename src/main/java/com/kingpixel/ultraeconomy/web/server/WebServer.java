package com.kingpixel.ultraeconomy.web.server;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.web.server.api.PlayerApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.PlayersApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.TransactionPlayerApiServlet;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import java.io.IOException;
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

      // -----------------------------
      // Configurar API
      // -----------------------------
      registerApiServlets(context);

      // -----------------------------
      // Configurar CORS si está en modo debug
      // -----------------------------
      if (UltraEconomy.config.isDebug()) {
        registerCors(context);
      }

      // -----------------------------
      // Servir archivos estáticos
      // -----------------------------
      registerStaticFiles(context);

      // -----------------------------
      // Filtro SPA fallback
      // -----------------------------
      registerSpaFallback(context);

      // -----------------------------
      // Iniciar servidor
      // -----------------------------
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

  // =============================
  // MÉTODOS PRIVADOS
  // =============================

  private void registerApiServlets(ServletContextHandler context) {
    context.addServlet(PlayersApiServlet.class, "/api/players");
    context.addServlet(TransactionPlayerApiServlet.class, "/api/transactions/player/*");
    context.addServlet(PlayerApiServlet.class, "/api/player/*");
  }

  private void registerCors(ServletContextHandler context) {
    FilterHolder cors = context.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
    cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,PUT,DELETE,OPTIONS");
    cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
  }

  private void registerStaticFiles(ServletContextHandler context) {
    context.setResourceBase(getClass().getClassLoader().getResource("web").toExternalForm());
    context.addServlet(DefaultServlet.class, "/"); // Sirve todos los archivos existentes
  }

  private void registerSpaFallback(ServletContextHandler context) {
    FilterHolder spaFallback = new FilterHolder(new Filter() {
      @Override
      public void init(FilterConfig filterConfig) {
      }

      @Override
      public void doFilter(ServletRequest request,
                           ServletResponse response,
                           FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Si es API o archivo existente, dejamos pasar
        if (path.startsWith("/api/") || resourceExists(path)) {
          chain.doFilter(request, response);
          return;
        }

        // Devolver index.html para SPA
        resp.setContentType("text/html");
        resp.getWriter().write(
          new String(getClass().getClassLoader()
            .getResourceAsStream("web/index.html")
            .readAllBytes())
        );
      }

      @Override
      public void destroy() {
      }

      private boolean resourceExists(String path) {
        return getClass().getClassLoader().getResource("web" + path) != null;
      }
    });

    context.addFilter(spaFallback, "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}
