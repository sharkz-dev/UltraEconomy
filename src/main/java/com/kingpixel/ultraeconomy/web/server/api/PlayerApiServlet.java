package com.kingpixel.ultraeconomy.web.server.api;

import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

public class PlayerApiServlet extends HttpServlet {

  @Override
  protected void doGet(
    HttpServletRequest req,
    HttpServletResponse resp
  ) throws IOException {

    resp.setContentType("application/json");

    // Obtener /{uuid}
    String pathInfo = req.getPathInfo();

    if (pathInfo == null || pathInfo.length() <= 1) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"UUID del jugador requerido\"}");
      return;
    }

    UUID playerUuid;
    try {
      playerUuid = UUID.fromString(pathInfo.substring(1));
    } catch (IllegalArgumentException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"UUID inválido\"}");
      return;
    }

    var account = DatabaseFactory.INSTANCE.getAccount(playerUuid);

    if (account == null) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      resp.getWriter().write("{\"error\":\"Jugador no encontrado\"}");
      return;
    }

    var json = Utils.newWithoutSpacingGson().toJson(account);

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(json);
  }
}
