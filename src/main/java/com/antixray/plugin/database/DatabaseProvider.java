package com.antixray.plugin.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseProvider {
    void initialize() throws SQLException;
    Connection getConnection() throws SQLException;
    void executeUpdate(String sql) throws SQLException;
    void shutdown() throws Exception;
    String getDatabaseType();
}
