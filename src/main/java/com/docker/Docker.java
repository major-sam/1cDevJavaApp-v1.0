package com.docker;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.net.InetAddress;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;


public class Docker {
    private JButton backupRestoreDBButton,quit;
    private JLabel sourcebasesize,targetbasesize,wmisapce;
    private JLabel status;
    private JProgressBar progressBar1,progressBar2;
    private JPanel main;
    private JList Serverlist,Targetlist,Sourcelist;
    private JScrollPane serverscrool, sourcescrool,targetscrool;
    private JCheckBox backupchk;
    private JLabel curentbak;
    private JPanel curenttasks;
    private JProgressBar ctaskBar;
    private JLabel task;
    private JTextField sourcesearch;
    private JTextField targetsearch;
    private List sourcebuffer, targetbuffer;
    private String sourceBase,targetBase, diskname ,server, warnmessage ,bakthreadstatus ,resthreadstatus,path,datapath,logpath,curbakdatabasename,curbakdatabasefinishtime;
    private Integer backupprogress,approve, restoreprogress,schedulcounter1,schedulcounter2;
// test comment
    Thread backupdb = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                bakthreadstatus ="WORKING";
                Connection conn = null;
                Statement stmt = null;
                ResultSet rs = null;
                String query="SET NOCOUNT ON " +
                        "BACKUP DATABASE ["+sourceBase+"] TO  DISK = N'current.bak' WITH NOFORMAT, INIT,  NAME = N'"+sourceBase+"-Full Database Backup', SKIP, NOREWIND, NOUNLOAD,  STATS = 10";
                String url ="jdbc:sqlserver://"+server+";user=login1c;password=Rhjrjlbk";
                conn = DriverManager.getConnection(url);
                stmt = conn.createStatement();
                rs = stmt.executeQuery(query);
               if (stmt.execute(query)) {
                    rs = stmt.getResultSet();
                }

            }
            catch (SQLException ex){
                System.out.println("SQLException: " + ex.getMessage());
                System.out.println("SQLState: " + ex.getSQLState());
                System.out.println("VendorError: " + ex.getErrorCode());
            }
            System.out.println("TASK "+bakthreadstatus);
            bakthreadstatus ="DONE";
        }
    });
    Thread restoredb = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                String logicsourceBase= null ,logicsourceBaseLog=null;
                resthreadstatus ="WORKING";
                System.out.println("TASK "+resthreadstatus);
                Connection conn = null;
                Statement stmt = null;
                ResultSet rs = null;
                String url ="jdbc:sqlserver://"+server+";user=login1c;password=Rhjrjlbk";
                conn = DriverManager.getConnection(url);
                stmt = conn.createStatement();
                String query= "RESTORE FILELISTONLY FROM DISK='current.bak' ";
                rs = stmt.executeQuery(query);
                if (stmt.execute(query)) {
                    rs = stmt.getResultSet();
                }
                while (rs.next()){
                    String type=rs.getString("Type");
                    String name=rs.getString("LogicalName");

                    if (type.matches(".*D.*"))
                    {
                       logicsourceBase = name;
                    }
                    if (type.matches(".*L.*"))
                    {
                       logicsourceBaseLog = name;
                    }
                }
                String queryres="USE [master]\n" +
                        "        ALTER DATABASE ["+targetBase+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE\n" +
                        "        RESTORE DATABASE ["+targetBase+"] FROM  DISK = N'current.bak' WITH  FILE = 1,  MOVE N'"+logicsourceBase+"' TO N'"+datapath+targetBase+".mdf',  MOVE N'"+logicsourceBaseLog+"' TO N'"+logpath+targetBase+"_log.ldf',  NOUNLOAD,  REPLACE,  STATS = 5\n" +
                        "        ALTER DATABASE ["+targetBase+"] SET MULTI_USER";
                conn = null;
                stmt = null;
                rs = null;
                conn = DriverManager.getConnection(url);
                stmt = conn.createStatement();
                rs = stmt.executeQuery(queryres);
                if (stmt.execute(queryres)) {
                    rs = stmt.getResultSet();
                }
            }
            catch (SQLException ex){
                System.out.println("SQLException: " + ex.getMessage());
                System.out.println("SQLState: " + ex.getSQLState());
                System.out.println("VendorError: " + ex.getErrorCode());
            }
            //System.out.println("TASK "+resthreadstatus);
            resthreadstatus ="DONE";
        }
    });

    public void disableui(){
        Serverlist.setEnabled(false);
        Sourcelist.setEnabled(false);
        Targetlist.setEnabled(false);
        backupRestoreDBButton.setEnabled(false);
        backupchk.setEnabled(false);
        quit.setEnabled(false);
        sourcesearch.setEnabled(false);
        targetsearch.setEnabled(false);
    }
    public void enableui(){
        Serverlist.setEnabled(true);
        Sourcelist.setEnabled(true);
        Targetlist.setEnabled(true);
        backupRestoreDBButton.setEnabled(true);
        backupchk.setEnabled(true);
        quit.setEnabled(true);
        sourcesearch.setEnabled(true);
        targetsearch.setEnabled(true);
    }

    public void restoreDB(){
        schedulcounter2 =0 ;
        ScheduledExecutorService schdres = Executors.newSingleThreadScheduledExecutor();
        schdres.schedule(restoredb, 1, TimeUnit.SECONDS);
        final ScheduledExecutorService schdreswatch = Executors.newSingleThreadScheduledExecutor();
        schdreswatch.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println(resthreadstatus);
                if (resthreadstatus == "DONE") {
                    Thread.currentThread().interrupt();
                    schedulcounter2++;
                    enableui();
                    String schdreswatchmsg= "<html><font color=#0565ff>Восстановление БД завершено</font>";
                    status.setText(schdreswatchmsg);
                    String resthreadstatusmsg="<html><font color=#00910a>Готово</font>";
                    status.paintImmediately(status.getVisibleRect());
                    if (schedulcounter2 == 2)
                    {
                        progressBar2.setValue(100);
                        status.setText(resthreadstatusmsg);
                        status.paintImmediately(status.getVisibleRect());
                    }
                    if (schedulcounter2 == 3)
                    {
                        resthreadstatus = "EXECUTED";
                        schdreswatch.shutdown();
                        progressBar2.setValue(100);
                        status.setText(resthreadstatusmsg);
                        status.paintImmediately(status.getVisibleRect());
                    }
                }
                if (resthreadstatus == "WORKING") {
                    String restoreDBmsg="<html><font color=#0565ff>Восстановление БД в процессе</font>";
                    status.setText(restoreDBmsg);
                    status.paintImmediately(status.getVisibleRect());
                    String query1 = "SELECT  r.session_id AS [Session_Id]\n" +
                            "             ,CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                            "             ,CONVERT(VARCHAR(1000), (\n" +
                            "             SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                            "             FROM sys.dm_exec_sql_text(sql_handle)\n" +
                            "             ))as 'query'\n" +
                            "             FROM sys.dm_exec_requests r\n" +
                            "             WHERE   command like 'RESTORE%'";
                    restoreprogress = progress(query1);
                    progressBar2.setValue(restoreprogress);
                    System.out.println("restoreprogress  status: " + restoreprogress);
                }
            }
        },0,5, TimeUnit.SECONDS);
    }
    public int progress(String query1){
        Connection conn1 = null;
        Statement stmt1 = null;
        ResultSet rs1 = null;
        int progress=0;
        try {
            String url ="jdbc:sqlserver://"+server+";user=login1c;password=Rhjrjlbk";
            conn1 = DriverManager.getConnection(url);
            stmt1 = conn1.createStatement();
            rs1 = stmt1.executeQuery(query1);
            if (stmt1.execute(query1)) {
                rs1 = stmt1.getResultSet();
            }
            while (rs1.next()){
                progress = Integer.parseInt(rs1.getString("Complete") ) ;
                            }

        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return  progress;
    }
    public int parcedisk(){
        String strRegEx = "<[^>]*>";
        String strsource=wmisapce.getText();
        String stringToSearch = strsource.replaceAll(strRegEx,"");
        Pattern p = Pattern.compile(diskname +".*");
        Matcher m = p.matcher(stringToSearch);
        System.out.println(m.find() ?
                "I found '"+m.group()+"' starting at index "+m.start()+" and ending at index "+m.end()+"." :
                "I found nothing!");
         int disk = Integer.parseInt(m.group().replaceAll("[^0-9]",""));

        return disk;
    }
    public int parcesize(){
       int source = Integer.parseInt(sourcebasesize.getText().replaceAll(" MB", ""));
       int target = Integer.parseInt(targetbasesize.getText().replaceAll(" MB", ""));
       int extra= source- target;
       return extra;
    }
    public String dbspace(JLabel targetlabel, String basename){
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            String url ="jdbc:sqlserver://"+ server +";user=login1c;password=Rhjrjlbk";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            String query="SELECT\n" +
                    "    D.name,\n" +
                    "    Substring((F.physical_name),0,3)  AS Drive,\n" +
                    "    CAST((F.size*8)/1024 AS VARCHAR(26))  AS FileSize\n" +
                    "FROM \n" +
                    "    sys.master_files F\n" +
                    "    INNER JOIN sys.databases D ON D.database_id = F.database_id\n" +
                    "Where type=0 and F.database_id >4 and D.name like '" + basename+"'";
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }
            while (rs.next()){
                String dbsize=rs.getString("FileSize");
                targetlabel.setText( dbsize +" MB" );
                diskname=rs.getString("Drive");
            }

        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return diskname;
    }
    public String mssqlgetpath(String query){
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            String url ="jdbc:sqlserver://"+ server +";user=login1c;password=Rhjrjlbk";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }

            while (rs.next()){
                path=rs.getString("a");
            }
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return path;
    }
    public String mssqlcurenttasks(String query){
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        String curentquery="empty";
        try {
            String url ="jdbc:sqlserver://"+ server +";user=login1c;password=Rhjrjlbk";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }

            while (rs.next()){
                curentquery=rs.getString("query");
            }
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return curentquery;
    }
    public void mssqlfreespace(String query){
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            String url ="jdbc:sqlserver://"+ server +";user=login1c;password=Rhjrjlbk";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }

            while (rs.next()){
                String dskspace="\n" +rs.getString("Drive") + " Free: " + rs.getString("FreeSpaceInMB");
                wmisapce.setText(wmisapce.getText() + dskspace + " MB<html><br/>");
            }
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }
    public void mssqlgetdb(String query){
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        List<String> workbase= new ArrayList<String>();
        try {
            BufferedReader abc = new BufferedReader(new FileReader("conf/base.properties"));
            String s;
            while((s = abc.readLine()) != null) {
                workbase.add(s);
            }
        }
        catch(Exception ex){
            System.out.println (ex.toString());
        }
        try {
        String url ="jdbc:sqlserver://"+ server +";user=login1c;password=Rhjrjlbk";
        conn =
                DriverManager.getConnection(url);
        stmt = conn.createStatement();
        rs = stmt.executeQuery(query);
        DefaultListModel sdb = new DefaultListModel();
        DefaultListModel tdb = new DefaultListModel();
        if (stmt.execute(query)) {
            rs = stmt.getResultSet();
        }
        while (rs.next()){
            String dbname=rs.getString("name");
            sdb.addElement(dbname);
            if (!workbase.contains(dbname)){
                tdb.addElement(dbname);
            }
        }
        sourcebuffer = getItems(sdb);
        targetbuffer = getItems(tdb);
        Sourcelist.setModel(sdb);
        Targetlist.setModel(tdb);
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }
    public String lastbackup(){
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            String url ="jdbc:sqlserver://"+ server +";user=login1c;password=Rhjrjlbk";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            String query="RESTORE HEADERONLY  FROM DISK='current.bak'";
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }
            while (rs.next()){
                curbakdatabasename=rs.getString("DatabaseName");
                curbakdatabasefinishtime=rs.getString("BackupFinishDate");
            }
            curentbak.setText("<html>Текущий бэкап:<br/>"
                    + curbakdatabasename +"<br/>"+ curbakdatabasefinishtime +"<html>" );
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return  curbakdatabasename;
    }
    private static List getItems(DefaultListModel model) {
        List list = new ArrayList();
        for (int i = 0; i < model.size(); i++) {
            list.add(model.elementAt(i).toString());
        }
        return list;
    }
    public Docker() {
        JFrame frame = new JFrame("Docker");

        DefaultListModel Smodel = new DefaultListModel();
        try {
            BufferedReader abc = new BufferedReader(new FileReader("conf/srv.properties"));
            String s;
            while((s = abc.readLine()) != null) {
                Smodel.addElement(s);
            }
        }
        catch(Exception ex){
            System.out.println (ex.toString());
        }
        Serverlist.setModel(Smodel);
        serverscrool.setViewportView(Serverlist);
        sourcescrool.setViewportView(Sourcelist);
        targetscrool.setViewportView(Targetlist);
        Serverlist.setLayoutOrientation(JList.VERTICAL);
        Serverlist.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                server = (String)Serverlist.getSelectedValue();
                String dblistquery="SELECT name FROM master.dbo.sysdatabases  where dbid >4";
                mssqlgetdb(dblistquery);
                String freespacequery ="SELECT DISTINCT dovs.logical_volume_name AS LogicalName,\n" +
                        "Substring(dovs.volume_mount_point,0,3) AS Drive,\n" +
                        "CONVERT(INT,dovs.available_bytes/1048576.0) AS FreeSpaceInMB\n" +
                        "FROM sys.master_files mf\n" +
                        "CROSS APPLY sys.dm_os_volume_stats(mf.database_id, mf.FILE_ID) dovs\n" +
                        "ORDER BY Drive ASC";
                wmisapce.setText("<html>");
                mssqlfreespace(freespacequery);
                String getdatapath= "select  SERVERPROPERTY('InstanceDefaultDataPath') as a";
                String getlogpath= "select  serverproperty('InstanceDefaultLogPath') as a";
                datapath = mssqlgetpath(getdatapath);
                logpath = mssqlgetpath(getlogpath);
                sourcebasesize.setText("");
                targetbasesize.setText("");
                lastbackup();
                backupchk.setVisible(true);
                curentbak.setVisible(true);
                final String query1="SELECT  r.session_id AS [Session_Id]\n" +
                        "             ,CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                        "             ,CONVERT(VARCHAR(1000), (\n" +
                        "             SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                        "             FROM sys.dm_exec_sql_text(sql_handle)\n" +
                        "             ))as 'query'\n" +
                        "             FROM sys.dm_exec_requests r\n" +
                        "             WHERE   command like 'BACKUP%' or command like 'RESTORE%'";
                String braction= mssqlcurenttasks(query1);
                backupRestoreDBButton.setEnabled(true);
                curenttasks.setVisible(false);
                status.setText("");
                status.paintImmediately(status.getVisibleRect());
                if (braction !="empty"){
                    curenttasks.setVisible(true);
                    backupRestoreDBButton.setEnabled(false);
                    String bakthreadstatusmsg="<html><font color=#ff0d0d>ЗАНЯТО</font>";
                    status.setText(bakthreadstatusmsg);
                    status.paintImmediately(status.getVisibleRect());
                    task.setText(braction.split("WITH")[0]);
                    final ScheduledExecutorService schdtaskwatch = Executors.newSingleThreadScheduledExecutor();
                    schdtaskwatch.scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                             if (mssqlcurenttasks(query1) =="empty") {
                                Thread.currentThread().interrupt();
                                schedulcounter1++;
                                if (schedulcounter1 == 2)
                                {
                                    System.out.println("THREAD IS STOPPED NOTHING TO DO");
                                    schdtaskwatch.shutdown();
                                    ctaskBar.setValue(100);
                                    curenttasks.setVisible(false);
                                    backupRestoreDBButton.setEnabled(true);
                                    status.setText("");
                                    status.paintImmediately(status.getVisibleRect());
                                }
                            }
                            else  {
                                String query1="SELECT  r.session_id AS [Session_Id]\n" +
                                        "             ,CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                                        "             ,CONVERT(VARCHAR(1000), (\n" +
                                        "             SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                                        "             FROM sys.dm_exec_sql_text(sql_handle)\n" +
                                        "             ))as 'query'\n" +
                                        "             FROM sys.dm_exec_requests r\n" +
                                        "             WHERE   command like 'BACKUP%' or command like 'RESTORE%'";
                                int taskprogress = progress(query1);
                                ctaskBar.setValue(taskprogress);
                                System.out.println("ctaskBar  status: " + taskprogress);
                            }
                        }
                    },0,10, TimeUnit.SECONDS);
                }

            }
        });
        backupRestoreDBButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                int extra = parcesize();
                int serverdisk= parcedisk();
                if (serverdisk<extra){
                    warnmessage = "<html><font color=#f54242> НЕДОСТАТОЧНО МЕСТА НА ДИСКЕ   </font>";
                     JOptionPane.showConfirmDialog(null,
                             warnmessage, "WARNING", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
                    approve = 1;
                }
                else {
                    warnmessage = "";
                    approve = JOptionPane.showConfirmDialog(null,
                        "Сервер: \t" + server + "\nИЗ: "+sourceBase+"\nВ: \t"+targetBase
                                +"\nДополнительное место: "+extra+" MB"
                                +"\nСвободно места на диске: "+serverdisk+" MB"
                                +"\n"+warnmessage  ,
                        "Подтверждение"
                        , JOptionPane.YES_NO_OPTION);
                }


                if(approve == 0){
                    Date date = new Date();
                    try {
                        FileWriter fileWriter  = new FileWriter("\\\\90500-ws108\\log$\\app.log",true);
                        PrintWriter log = new PrintWriter(fileWriter);
                        log.println(date+" "+System.getProperty("user.name") + " " + InetAddress.getLocalHost().getHostName()+ " " + server +" From: \t" +sourceBase+ " To: \t"+targetBase );
                        log.close();
                    }
                    catch (IOException e){
                        System.out.println("file not found");
                    }
                    disableui();
                    String approvemsg="<html><font color=#0565ff>Бэкап БД в процессе</font>";
                    status.setText(approvemsg);
                    status.paintImmediately(status.getVisibleRect());
                    progressBar1.setMinimum(0);
                    progressBar1.setMaximum(100);
                    progressBar1.setValue(0);
                    progressBar2.setMinimum(0);
                    progressBar2.setMaximum(100);
                    progressBar2.setValue(0);
                    schedulcounter1=0;
                    schedulcounter2=0;
                    if (!backupchk.isSelected())  {
                        System.out.println(backupchk.isSelected());
                        ScheduledExecutorService schdbak = Executors.newSingleThreadScheduledExecutor();
                        schdbak.schedule(backupdb, 1, TimeUnit.SECONDS);
                        final ScheduledExecutorService schdbakwatch = Executors.newSingleThreadScheduledExecutor();
                        schdbakwatch.scheduleAtFixedRate(new Runnable() {
                            @Override
                            public void run() {
                                if (bakthreadstatus == "DONE") {
                                    Thread.currentThread().interrupt();
                                    schedulcounter1++;
                                    String bakthreadstatusmsg="<html><font color=#0565ff>Бэкап БД завершен</font>";
                                    status.setText(bakthreadstatusmsg);
                                    status.paintImmediately(status.getVisibleRect());
                                    if (schedulcounter1 == 2)
                                    {
                                        bakthreadstatus = "EXECUTED";
                                        System.out.println("THREAD IS STOPPED NOTHING TO DO");
                                        System.out.println(bakthreadstatus);
                                        schdbakwatch.shutdown();
                                        progressBar1.setValue(100);
                                        restoreDB();
                                    }
                                }
                                if (bakthreadstatus == "WORKING") {
                                    String query1="SELECT  r.session_id AS [Session_Id]\n" +
                        "             ,CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                        "             ,CONVERT(VARCHAR(1000), (\n" +
                        "             SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                        "             FROM sys.dm_exec_sql_text(sql_handle)\n" +
                        "             ))as 'query'\n" +
                        "             FROM sys.dm_exec_requests r\n" +
                        "             WHERE   command like 'BACKUP%'";
                                    backupprogress = progress(query1);
                                    progressBar1.setValue(backupprogress);
                                    System.out.println("backupprogress from status: " + backupprogress);
                                }
                            }
                        },0,5, TimeUnit.SECONDS);
                    }
                    else {
                        System.out.println(backupchk.isSelected());
                        progressBar1.setValue(100);
                        restoreDB();
                    }
                }
            }
        });
        quit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                System.exit(0);
            }
        });
        Sourcelist.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                sourceBase = (String)Sourcelist.getSelectedValue();
                dbspace(sourcebasesize, sourceBase);
            }
        });
        Targetlist.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                targetBase = (String)Targetlist.getSelectedValue();
                String diskname=dbspace(targetbasesize, targetBase);
            }
        });
        backupchk.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if(e.getStateChange() == 1){
                    Sourcelist.setEnabled(false);
                    dbspace(sourcebasesize,lastbackup());
                }
                else {
                    Sourcelist.setEnabled(true);
                    dbspace(sourcebasesize, (String)Sourcelist.getSelectedValue());
                }
            }
        });
        sourcesearch.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter();}

            private void filter() {
                DefaultListModel model = (DefaultListModel) Sourcelist.getModel();
                model.clear();
                String s = sourcesearch.getText();
                targetsearch.setText(s);
                for (int i=0;i< sourcebuffer.size();i++){
                    if ( sourcebuffer.get(i).toString().contains(s)){
                        model.addElement( sourcebuffer.get(i).toString());
                    }
                }
                Sourcelist.setModel(model);
            }
        });
        targetsearch.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter();}

            private void filter() {
                DefaultListModel model1 = (DefaultListModel) Targetlist.getModel();
                model1.clear();
                String s = targetsearch.getText();
                for (int i=0;i< targetbuffer.size();i++){
                    if ( targetbuffer.get(i).toString().contains(s)){
                        model1.addElement( targetbuffer.get(i).toString());
                    }
                }
                Targetlist.setModel(model1);
            }
        });

        ImageIcon img = new ImageIcon("conf/d2.png");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(img.getImage());
        frame.setMinimumSize(new Dimension(800, 600));
        frame.setPreferredSize(new Dimension(800, 600));
        frame.add(main);
        frame.pack();
        frame.setVisible(true);
        curenttasks.setVisible(false);
        backupchk.setVisible(false);
        curentbak.setVisible(false);
    }

    public static void main(String[] args){
        final Docker docker = new Docker();
    }

}