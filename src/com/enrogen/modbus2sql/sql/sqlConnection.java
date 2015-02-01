package com.enrogen.modbus2sql.sql;

import com.enrogen.modbus2sql.appInterface.appInterface;
import static com.enrogen.modbus2sql.appInterface.appInterface.SQL_KEEP_ALIVE_INTERVAL;
import com.enrogen.modbus2sql.javafx.windowcontroller.mainWindowController;
import com.enrogen.modbus2sql.logger.EgLogger;
import com.enrogen.modbus2sql.mainWindow;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class sqlConnection implements appInterface {

    //Standard variables
    private static sqlConnection sqlConnectionSingleton;
    private String SQLServerString;
    private String SQLUserString;
    private String SQLPasswordString;
    private String SQLdefaultDatabaseString;
    private final boolean debug;
    private boolean SqlAlive = false;
    private boolean Restarted = false;
    private ResultSetMetaData rsmd = null;
    private int ResultColCount = 0;
    public List BatchSQLCommands = new LinkedList();
    public Connection connection = null;
    private Timeline sqlWatchdogTimer = null;

    public sqlConnection() {
        if (Boolean.valueOf(System.getProperty("com.enrogen.modbus2sql.sql.debug"))) {
            debug = true;
            EgLogger.logInfo("Debugging ON");
        } else {
            debug = false;
        }

        //Init the watchdog timer
        sqlWatchdogTimer();
    }

    //Create singleton instance
    public static sqlConnection getInstance() {
        if (sqlConnectionSingleton == null) {
            sqlConnectionSingleton = new sqlConnection();
        }
        return sqlConnectionSingleton;
    }

    //Main routine
    public void StartSQL() {
        //get a reference to the mainWindow
        mainWindowController mwc = mainWindow.getInstance().getMainWindowController();

        //Get SQL parameters
        String sqlServerIP = mwc.getText_sqlserverip();
        String sqlUsername = mwc.getText_sqlusername();
        String sqlPassword = mwc.getText_sqlpassword();
        String sqlDatabaseName = mwc.getText_sqldatabasename();

        //Open the SQL Connection
        setSQLParams(sqlServerIP, sqlUsername, sqlPassword, sqlDatabaseName);
        EgLogger.logInfo("Starting SQL Connection");
        restartSQLConnection();
        EgLogger.logInfo("Starting SQL Keep Alive");
        StartKeepAlive();
    }

    private void setSQLParams(String SQLServer, String SQLUser, String SQLPassword, String defaultDatabase) {
        SQLServerString = SQLServer;
        SQLUserString = SQLUser;
        SQLPasswordString = SQLPassword;
        SQLdefaultDatabaseString = defaultDatabase;
        if (debug) {
            EgLogger.logInfo("SQLServer : " + SQLServerString);
            EgLogger.logInfo("SQLUser : " + SQLUserString);
            EgLogger.logInfo("SQLPasswordString : " + SQLPassword);
            EgLogger.logInfo("SQLdefaultDatabaseString : " + SQLdefaultDatabaseString);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //Open Close and Restart the SQL Connection
    ////////////////////////////////////////////////////////////////////////
    private boolean openSQLConnection() {
        String driverName = MYSQL_DRIVER_NAME;
        String SQLUrl = MYSQL_CONNECTION_STRING_START + SQLServerString + "/" + SQLdefaultDatabaseString;

        try {
            Class.forName(driverName);
            connection = DriverManager.getConnection(SQLUrl, SQLUserString, SQLPasswordString);
            if (debug) {
                EgLogger.logInfo("Opened SQL Connection");
            }
            return true;
        } catch (ClassNotFoundException e) {
            EgLogger.logSevere("Error at openSQLConnection");
            EgLogger.logSevere("SQLURL : " + SQLUrl);
            EgLogger.logSevere("driverName : " + driverName);
        } catch (SQLException sqle) {
            EgLogger.logSevere("Error at openSQLConnection");
            EgLogger.logSevere("SQLURL : " + SQLUrl);
            EgLogger.logSevere("driverName : " + driverName);
        } catch (Exception e) {
            EgLogger.logSevere("Error at openSQLConnection");
            EgLogger.logSevere("SQLURL : " + SQLUrl);
            EgLogger.logSevere("driverName : " + driverName);
        }
        SqlAlive = false;
        return false;
    }

    //Close the standard connection to the MySQL Server
    private void closeSQLConnection() {
        try {
            if (connection != null) {
                if (!connection.isClosed()) {
                    connection.close();
                }
            }
            if (debug) {
                EgLogger.logInfo("Closed SQL Connection");
            }
        } catch (SQLException sqle) {
            EgLogger.logSevere("Error at closeSQLConnection");
        } catch (Exception e) {
            EgLogger.logSevere("Error at closeSQLConnection");
        }
    }

    private void restartSQLConnection() {
        closeSQLConnection();
        openSQLConnection();
        Restarted = true;
    }

    ////////////////////////////////////////////////////////////////////////
    //Test SQL with Simple Command
    ////////////////////////////////////////////////////////////////////////
    private boolean checkSQLConnection() {
        boolean Alive = false;

        //Run the SQL command
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(SQL_TEST_COMMAND);
            while (rs.next()) {
                Alive = true;
            }
            stmt.close();
        } catch (SQLException sqle) {
            EgLogger.logSevere("Error at checkSQLConnection");
        } catch (Exception e) {
            EgLogger.logSevere("Error at checkSQLConnection");
        }
        SqlAlive = Alive;
        if (debug) {
            EgLogger.logInfo("SQL Ping : " + Alive);
        }
        return Alive;
    }

    public boolean isAlive() {
        return SqlAlive;
    }

    ////////////////////////////////////////////////////////////////////////
    //SQL Insert and Select
    ////////////////////////////////////////////////////////////////////////
    public List<List> SQLSelectCommand(String sqlcmd) {
        List<List> resultList = new LinkedList();
        List<Object> resultValue = new LinkedList();

        //Run the SQL command
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sqlcmd);
            rsmd = rs.getMetaData();
            ResultColCount = rsmd.getColumnCount();

            while (rs.next()) {
                for (int x = 0; x < rsmd.getColumnCount(); x++) {
                    Object o = rs.getObject(x + 1);
                    resultValue.add(o);
                }
                resultList.add(new LinkedList(resultValue));
                resultValue.clear();
            }
            stmt.close();
        } catch (SQLException sqle) {
            ResultColCount = 0;
            EgLogger.logSevere("Error at SQLSelectCommand");
            EgLogger.logSevere("Command was :-");
            EgLogger.logSevere(sqlcmd);
        }
        return resultList;
    }

    public int getResultColumnCount() {
        return ResultColCount;
    }

    //Use for insert and update
    public void SQLUpdateCommand(String sqlcmd) {
        //Run the SQL command
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(sqlcmd);
            stmt.close();
        } catch (SQLException sqle) {
            EgLogger.logSevere("Error at SQLUpdateCommand");
            EgLogger.logSevere("Command was :-");
            EgLogger.logSevere(sqlcmd);
        } catch (Exception e) {
            EgLogger.logSevere("Error at SQLUpdateCommand");
            EgLogger.logSevere("Command was :-");
            EgLogger.logSevere(sqlcmd);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //SQL Batch Commands
    ////////////////////////////////////////////////////////////////////////
    public void ClearBatch() {
        BatchSQLCommands.clear();
    }

    public void AddToBatch(String sqlcmd) {
        BatchSQLCommands.add(sqlcmd);
    }

    public void SQLExecuteBatch() {
        //Run the SQL command
        try {
            Statement stmt = connection.createStatement();

            for (int i = 0; i < BatchSQLCommands.size(); i++) {
                stmt.addBatch(BatchSQLCommands.get(i).toString());
            }
            stmt.executeBatch();
            stmt.close();
        } catch (Exception e) {
            EgLogger.logSevere("Error at SQLExecuteBatchCommand");
            EgLogger.logSevere("Printing out batch list :-");
            for (int i = 0; i < BatchSQLCommands.size(); i++) {
                EgLogger.logSevere(BatchSQLCommands.get(i).toString());
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //Keep SQL Alive (stop SQL Timing out)
    ////////////////////////////////////////////////////////////////////////
    private void CheckAndRestartSQLCon() {
        checkSQLConnection();
        if (!isAlive()) {
            if (debug) {
                EgLogger.logSevere("Attempting to Restart SQL Connection");
            }
            restartSQLConnection();
            if (debug) {
                if (isAlive()) {
                    EgLogger.logInfo("Success");
                } else {
                    EgLogger.logSevere("Failed");
                }
            }
        }
    }

    private void StartKeepAlive() {
        if (debug) {
            EgLogger.logInfo("Starting keepalive Thread: " + SQL_KEEP_ALIVE_INTERVAL + "msec");
        }
        sqlWatchdogTimer.play();
    }

    private void StopKeepAlive() {
        if (debug) {
            EgLogger.logInfo("Stopping keepalive Thread");
        }
        sqlWatchdogTimer.stop();
    }

    //The timer to keep alive + update isAlive for lamps
    private void sqlWatchdogTimer() {
        if (sqlWatchdogTimer == null) {
            //Create a thread to flash the lamps
            EgLogger.logInfo("Starting sql Watchdog Ticker at : " + SQL_KEEP_ALIVE_INTERVAL + "mSec");

            sqlWatchdogTimer = new Timeline(new KeyFrame(
                    Duration.millis(SQL_KEEP_ALIVE_INTERVAL),
                    ae -> CheckAndRestartSQLCon()));
            sqlWatchdogTimer.setCycleCount(Animation.INDEFINITE);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //Flags for Restarted Server
    ////////////////////////////////////////////////////////////////////////
    public boolean isRestarted() {
        return Restarted;
    }

    public void resetRestartedFlag() {
        Restarted = false;
    }

}