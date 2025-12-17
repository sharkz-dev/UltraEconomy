package com.kingpixel.ultraeconomy.web.server.api;

import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class PlayersApiServlet extends HttpServlet {

  @Override
  protected void doGet(
    HttpServletRequest req,
    HttpServletResponse resp
  ) throws IOException {

    resp.setContentType("application/json");
    resp.setStatus(HttpServletResponse.SC_OK);

    var accounts = DatabaseFactory.INSTANCE.getAccounts(50);
    var json = Utils.newWithoutSpacingGson().toJson(accounts);
    resp.getWriter().write(json);
  }
}
