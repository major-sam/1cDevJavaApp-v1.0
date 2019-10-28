package com.docker;

import org.jasypt.util.text.StrongTextEncryptor;

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
import java.util.*;
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
    private JButton backup_restore_db_button,quit;
    private JLabel source_base_size, target_base_size, wmi_space;
    private JLabel status;
    private JProgressBar progressbar1, progressbar2;
    private JPanel main;
    private JList server_list, target_list, source_list;
    private JScrollPane server_scroll, source_scroll, target_scroll;
    private JCheckBox backup_check;
    private JLabel current_bak;
    private JPanel current_tasks;
    private JProgressBar c_task_bar;
    private JLabel task;
    private JTextField source_search;
    private JTextField target_search;
    private List source_buffer, target_buffer;
    private String source_base, target_base, disk_name,server, warn_message, bak_thread_status, res_thread_status, path,
            data_path, log_path, cur_bak_database_name, cur_bak_database_finish_time, user_name, user_password;
    private Integer backup_progress,approve, restore_progress, scheduler_counter1, scheduler_counter2;
    private Thread backup_db = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                bak_thread_status ="WORKING";
                Connection conn;
                Statement stmt;
                String query="SET NOCOUNT ON " +
                        "BACKUP DATABASE ["+ source_base +"] TO  DISK = N'current.bak' WITH NOFORMAT, INIT,  NAME = N'"+ source_base +"-Full Database Backup', SKIP, NOREWIND, NOUNLOAD,  STATS = 10";
                String url ="jdbc:sqlserver://"+server+";user="+user_name+";password="+user_password+"";
                conn = DriverManager.getConnection(url);
                stmt = conn.createStatement();
                stmt.executeQuery(query);
                if (stmt.execute(query)) {
                    stmt.getResultSet();
                }

            }
            catch (SQLException ex){
                System.out.println("SQLException: " + ex.getMessage());
                System.out.println("SQLState: " + ex.getSQLState());
                System.out.println("VendorError: " + ex.getErrorCode());
            }
            bak_thread_status ="DONE";
        }
    });
    private Thread restore_db = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                String logic_source_base= null ,logic_source_base_log=null;
                res_thread_status ="WORKING";
                Connection conn;
                Statement stmt;
                ResultSet rs;
                String url ="jdbc:sqlserver://"+server+";user="+user_name+";password="+user_password+"";
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
                       logic_source_base = name;
                    }
                    if (type.matches(".*L.*"))
                    {
                       logic_source_base_log = name;
                    }
                }
                String query_res="USE [master]\n" +
                        "        ALTER DATABASE ["+ target_base +"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE\n" +
                        "        RESTORE DATABASE ["+ target_base +"] FROM  DISK = N'current.bak' WITH  FILE = 1,  MOVE N'"+logic_source_base+"' TO N'"+ data_path + target_base +".mdf',  MOVE N'"+logic_source_base_log+"' TO N'"+ log_path + target_base +"_log.ldf',  NOUNLOAD,  REPLACE,  STATS = 5\n" +
                        "        ALTER DATABASE ["+ target_base +"] SET MULTI_USER";
                conn = DriverManager.getConnection(url);
                stmt = conn.createStatement();
                stmt.executeQuery(query_res);
                if (stmt.execute(query_res)) {
                    stmt.getResultSet();
                }
            }
            catch (SQLException ex){
                System.out.println("SQLException: " + ex.getMessage());
                System.out.println("SQLState: " + ex.getSQLState());
                System.out.println("VendorError: " + ex.getErrorCode());
            }
            res_thread_status ="DONE";
        }
    });

    private void disable_ui(){
        server_list.setEnabled(false);
        source_list.setEnabled(false);
        target_list.setEnabled(false);
        backup_restore_db_button.setEnabled(false);
        backup_check.setEnabled(false);
        quit.setEnabled(false);
        source_search.setEnabled(false);
        target_search.setEnabled(false);
    }
    private void enable_ui(){
        server_list.setEnabled(true);
        source_list.setEnabled(true);
        target_list.setEnabled(true);
        backup_restore_db_button.setEnabled(true);
        backup_check.setEnabled(true);
        quit.setEnabled(true);
        source_search.setEnabled(true);
        target_search.setEnabled(true);
    }

    private void restore_db(){
        scheduler_counter2 =0 ;
        ScheduledExecutorService scheduler_res = Executors.newSingleThreadScheduledExecutor();
        scheduler_res.schedule(restore_db, 1, TimeUnit.SECONDS);
        final ScheduledExecutorService scheduled_res_watch = Executors.newSingleThreadScheduledExecutor();
        scheduled_res_watch.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (res_thread_status != null && res_thread_status.equals("DONE")) {
                    Thread.currentThread().interrupt();
                    scheduler_counter2++;
                    enable_ui();
                    String scheduled_res_watch_msg= "<html><font color=#0565ff>Восстановление БД завершено</font>";
                    status.setText(scheduled_res_watch_msg);
                    String res_thread_status_msg="<html><font color=#00910a>Готово</font>";
                    status.paintImmediately(status.getVisibleRect());
                    if (scheduler_counter2 == 1)
                    {
                        progressbar2.setValue(100);
                        status.setText(res_thread_status_msg);
                        status.paintImmediately(status.getVisibleRect());
                    }
                    if (scheduler_counter2 == 2)
                    {
                        res_thread_status = "EXECUTED";
                        scheduled_res_watch.shutdown();
                        progressbar2.setValue(100);
                        status.setText(res_thread_status_msg);
                        status.paintImmediately(status.getVisibleRect());
                    }
                }
                if (res_thread_status != null && res_thread_status.equals("WORKING")) {
                    String restore_db_msg="<html><font color=#0565ff>Восстановление БД в процессе</font>";
                    status.setText(restore_db_msg);
                    status.paintImmediately(status.getVisibleRect());
                    String query1 = "SELECT  r.session_id AS [Session_Id]\n" +
                            "             ,CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                            "             ,CONVERT(VARCHAR(1000), (\n" +
                            "             SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                            "             FROM sys.dm_exec_sql_text(sql_handle)\n" +
                            "             ))as 'query'\n" +
                            "             FROM sys.dm_exec_requests r\n" +
                            "             WHERE   command like 'RESTORE%'";
                    restore_progress = progress(query1);
                    progressbar2.setValue(restore_progress);
                }
            }
        },0,5, TimeUnit.SECONDS);
    }
    private int progress(String query1){
        Connection conn1;
        Statement stmt1;
        ResultSet rs1;
        int progress=0;
        try {
            String url ="jdbc:sqlserver://"+server+";user="+user_name+";password="+user_password+"";
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
    private int parse_disk(){
        String strRegEx = "<[^>]*>";
        String str_source= wmi_space.getText();
        String stringToSearch = str_source.replaceAll(strRegEx,"");
        Pattern p = Pattern.compile(disk_name +".*");
        Matcher m = p.matcher(stringToSearch);
        System.out.println(m.find() ?
                "I found '"+m.group()+"' starting at index "+m.start()+" and ending at index "+m.end()+"." :
                "I found nothing!");

        return Integer.parseInt(m.group().replaceAll("[^0-9]",""));
    }
    private int parse_size(){
       int source = Integer.parseInt(source_base_size.getText().replaceAll(" MB", ""));
       int target = Integer.parseInt(target_base_size.getText().replaceAll(" MB", ""));
        return source - target;
    }
    private void db_space(JLabel target_label, String basename){
        Connection conn;
        Statement stmt;
        ResultSet rs;
        try {
            String url ="jdbc:sqlserver://"+ server +";user="+user_name+";password="+user_password+"";
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
                String db_size=rs.getString("FileSize");
                target_label.setText( db_size +" MB" );
                disk_name =rs.getString("Drive");
            }

        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }
    private String mssql_get_path(String query){
        Connection conn;
        Statement stmt;
        ResultSet rs;
        try {
            String url ="jdbc:sqlserver://"+ server +";user="+user_name+";password="+user_password+"";
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
    private String mssql_current_tasks(){
        Connection conn;
        Statement stmt;
        ResultSet rs;
        String current_query="empty";
        String query="SELECT  r.session_id AS [Session_Id]" +
                ",CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                ",CONVERT(VARCHAR(1000), (\n" +
                "SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                "FROM sys.dm_exec_sql_text(sql_handle)\n" +
                "))as 'query'\n" +
                "FROM sys.dm_exec_requests r\n" +
                "WHERE   command like 'BACKUP%' or command like 'RESTORE%'";
        try {
            String url ="jdbc:sqlserver://"+ server +";user="+user_name+";password="+user_password+"";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }

            while (rs.next()){
                current_query=rs.getString("query");
            }
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return current_query;
    }
    private void mssql_free_space(String query){
        Connection conn;
        Statement stmt;
        ResultSet rs;
        try {
            String url ="jdbc:sqlserver://"+ server +";user="+user_name+";password="+user_password+"";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }

            while (rs.next()){
                String disk_space="\n" +rs.getString("Drive") + " Free: " + rs.getString("FreeSpaceInMB");
                wmi_space.setText(wmi_space.getText() + disk_space + " MB<html><br/>");
            }
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }
    private void mssql_get_db(String query){
        Connection conn;
        Statement stmt;
        ResultSet rs;
        List<String> work_base= new ArrayList<String>();
        try {
            BufferedReader abc = new BufferedReader(new FileReader("conf/base.properties"));
            String s;
            while((s = abc.readLine()) != null) {
                work_base.add(s);
            }
        }
        catch(Exception ex){
            System.out.println (ex.toString());
        }
        try {
        String url ="jdbc:sqlserver://"+ server +";user="+user_name+";password="+user_password+"";
        conn =
                DriverManager.getConnection(url);
        stmt = conn.createStatement();
        rs = stmt.executeQuery(query);
        DefaultListModel sdb  = new DefaultListModel();
        DefaultListModel tdb = new DefaultListModel();
        if (stmt.execute(query)) {
            rs = stmt.getResultSet();
        }
        while (rs.next()){
            String dbname=rs.getString("name");
            sdb.addElement(dbname);
            if (!work_base.contains(dbname)){
                tdb.addElement(dbname);
            }
        }
        source_buffer = getItems(sdb);
        target_buffer = getItems(tdb);
        source_list.setModel(sdb);
        target_list.setModel(tdb);
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }
    private String last_backup(){
        Connection conn;
        Statement stmt;
        ResultSet rs;
        try {
            String url ="jdbc:sqlserver://"+ server +";user="+user_name+";password="+user_password+"";
            conn =
                    DriverManager.getConnection(url);
            stmt = conn.createStatement();
            String query="RESTORE HEADERONLY  FROM DISK='current.bak'";
            rs = stmt.executeQuery(query);
            if (stmt.execute(query)) {
                rs = stmt.getResultSet();
            }
            while (rs.next()){
                cur_bak_database_name =rs.getString("DatabaseName");
                cur_bak_database_finish_time =rs.getString("BackupFinishDate");
            }
            current_bak.setText("<html>Текущий бэкап:<br/>"
                    + cur_bak_database_name +"<br/>"+ cur_bak_database_finish_time +"<html>" );
        }
        catch (SQLException ex){
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return cur_bak_database_name;
    }
    private static List getItems(DefaultListModel model) {
        List list = new ArrayList();
        for (int i = 0; i < model.size(); i++) {
            list.add(model.elementAt(i).toString());
        }
        return list;
    }
    private Docker() {
        StrongTextEncryptor textEncryptor = new StrongTextEncryptor();
        textEncryptor.setPassword("$ecurePWD");
        JFrame frame = new JFrame("Docker");
        FileInputStream fis;
        Properties property = new Properties();
        DefaultListModel s_model = new DefaultListModel();
        try {
            fis = new FileInputStream("conf/default.properties");
            property.load(fis);
            String crypt_name = property.getProperty("user");
            String crypt_password = property.getProperty("password");
            while (true) {
                if (crypt_name == null | crypt_password == null) {
                    JPanel panel = new JPanel(new BorderLayout(5, 7));
                    JPanel labels = new JPanel(new GridLayout(0, 1, 2, 2));
                    labels.add(new JLabel("Login:", SwingConstants.RIGHT));
                    labels.add(new JLabel("Pass:", SwingConstants.RIGHT));
                    labels.add(new JLabel("Confirm:", SwingConstants.RIGHT));
                    panel.add(labels, BorderLayout.WEST);
                    JPanel fields = new JPanel(new GridLayout(0, 1, 2, 2));
                    JTextField username = new JTextField(textEncryptor.decrypt(crypt_name));
                    JPasswordField pass = new JPasswordField(10);
                    JPasswordField pass_confirm = new JPasswordField(10);
                    fields.add(username);
                    fields.add(pass);
                    fields.add(pass_confirm);
                    panel.add(fields, BorderLayout.CENTER);
                    String[] options = new String[]{"OK", "Cancel"};
                    int option = JOptionPane.showOptionDialog(null, panel, "Enter Credentials For MSSQL Server",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                            null, options, options[0]);
                    if (option == 0) // pressing OK button
                    {
                        String name = username.getText();
                        String password = new String( pass.getPassword());
                        String confirm = new String( pass_confirm.getPassword());
                        if (!password.equals(confirm) | password.isEmpty()){
                            JOptionPane  bad_confirm = new JOptionPane ("Bad password confirm or empty password", JOptionPane.ERROR_MESSAGE);
                            JDialog dialog = bad_confirm.createDialog("Confirm Not Match");
                            dialog.setAlwaysOnTop(true);
                            dialog.setVisible(true);
                        }
                        else {
                            Properties prop = new Properties();
                            crypt_name = textEncryptor.encrypt(name);
                            crypt_password = textEncryptor.encrypt(password);
                            prop.setProperty("user",crypt_name);
                            prop.setProperty("password",crypt_password);
                            String conf_path = ".\\conf\\default.properties";
                            prop.store(new FileOutputStream(conf_path,true), "\nremove lines if password changes or wrong");
                            System.out.println("Your password is: " + password + "\nYour password confirm is: " + confirm);
                            break;
                        }
                    }
                    else {
                        System.exit(0);
                    }
                }
                else {
                    break;
                }
            }
            user_name = textEncryptor.decrypt(crypt_name);
            user_password = textEncryptor.decrypt(crypt_password);

            String[] servers_property = property.getProperty("servers").split(",");
            List<String> al;
            al = Arrays.asList(servers_property);
            for(String s: al){
                s_model.addElement(s);
            }
        }
        catch(Exception ex){
            System.out.println (ex.toString());
        }

        server_list.setModel(s_model);
        server_scroll.setViewportView(server_list);
        source_scroll.setViewportView(source_list);
        target_scroll.setViewportView(target_list);
        server_list.setLayoutOrientation(JList.VERTICAL);
        server_list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                server = (String) server_list.getSelectedValue();
                String db_list_query="SELECT name FROM master.dbo.sysdatabases  where dbid >4";
                mssql_get_db(db_list_query);
                String free_space_query ="SELECT DISTINCT dovs.logical_volume_name AS LogicalName,\n" +
                        "Substring(dovs.volume_mount_point,0,3) AS Drive,\n" +
                        "CONVERT(INT,dovs.available_bytes/1048576.0) AS FreeSpaceInMB\n" +
                        "FROM sys.master_files mf\n" +
                        "CROSS APPLY sys.dm_os_volume_stats(mf.database_id, mf.FILE_ID) dovs\n" +
                        "ORDER BY Drive ASC";
                wmi_space.setText("<html>");
                mssql_free_space(free_space_query);
                String get_data_path= "select  SERVERPROPERTY('InstanceDefaultDataPath') as a";
                String get_log_path= "select  serverproperty('InstanceDefaultLogPath') as a";
                data_path = mssql_get_path(get_data_path);
                log_path = mssql_get_path(get_log_path);
                source_base_size.setText("");
                target_base_size.setText("");
                last_backup();
                backup_check.setVisible(true);
                current_bak.setVisible(true);
                String br_action = mssql_current_tasks();
                backup_restore_db_button.setEnabled(true);
                current_tasks.setVisible(false);
                status.setText("");
                status.paintImmediately(status.getVisibleRect());
                if (!br_action.equals("empty")){
                    current_tasks.setVisible(true);
                    backup_restore_db_button.setEnabled(false);
                    String bak_thread_status_msg="<html><font color=#ff0d0d>ЗАНЯТО</font>";
                    status.setText(bak_thread_status_msg);
                    status.paintImmediately(status.getVisibleRect());
                    task.setText(br_action.split("WITH")[0]);
                    final ScheduledExecutorService scheduled_task_watch = Executors.newSingleThreadScheduledExecutor();
                    scheduled_task_watch.scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                             if (mssql_current_tasks().equals("empty")) {
                                Thread.currentThread().interrupt();
                                scheduler_counter1++;
                                if (scheduler_counter1 == 2)
                                {
//                                    THREAD IS STOPPED NOTHING TO DO
                                    scheduled_task_watch.shutdown();
                                    c_task_bar.setValue(100);
                                    current_tasks.setVisible(false);
                                    backup_restore_db_button.setEnabled(true);
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
                                int task_progress = progress(query1);
                                c_task_bar.setValue(task_progress);
                            }
                        }
                    },0,10, TimeUnit.SECONDS);
                }

            }
        });
        backup_restore_db_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                int extra = parse_size();
                int server_disk= parse_disk();
                if (server_disk<extra){
                    warn_message = "<html><font color=#f54242> НЕДОСТАТОЧНО МЕСТА НА ДИСКЕ   </font>";
                     JOptionPane.showConfirmDialog(null,
                             warn_message, "WARNING", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
                    approve = 1;
                }
                else {
                    warn_message = "";
                    approve = JOptionPane.showConfirmDialog(null,
                        "Сервер: \t" + server + "\nИЗ: "+ source_base +"\nВ: \t"+ target_base
                                +"\nДополнительное место: "+extra+" MB"
                                +"\nСвободно места на диске: "+server_disk+" MB"
                                +"\n"+ warn_message,
                        "Подтверждение"
                        , JOptionPane.YES_NO_OPTION);
                }


                if(approve == 0){
                    Date date = new Date();
                    try {
                        FileWriter fileWriter  = new FileWriter("\\\\90500-ws108\\log$\\app.log",true);
                        PrintWriter log = new PrintWriter(fileWriter);
                        log.println(date+" "+System.getProperty("user.name") + " " + InetAddress.getLocalHost().getHostName()+ " " + server +" From: \t" + source_base + " To: \t"+ target_base);
                        log.close();
                    }
                    catch (IOException e){
                        System.out.println("file not found");
                    }
                    disable_ui();
                    String approve_msg="<html><font color=#0565ff>Бэкап БД в процессе</font>";
                    status.setText(approve_msg);
                    status.paintImmediately(status.getVisibleRect());
                    progressbar1.setMinimum(0);
                    progressbar1.setMaximum(100);
                    progressbar1.setValue(0);
                    progressbar2.setMinimum(0);
                    progressbar2.setMaximum(100);
                    progressbar2.setValue(0);
                    scheduler_counter1 =0;
                    scheduler_counter2 =0;
                    if (!backup_check.isSelected())  {
                        System.out.println(backup_check.isSelected());
                        ScheduledExecutorService scheduler_bak = Executors.newSingleThreadScheduledExecutor();
                        scheduler_bak.schedule(backup_db, 1, TimeUnit.SECONDS);
                        final ScheduledExecutorService scheduled_bak_watch = Executors.newSingleThreadScheduledExecutor();
                        scheduled_bak_watch.scheduleAtFixedRate(new Runnable() {
                            @Override
                            public void run() {
                                if (bak_thread_status != null && bak_thread_status.equals("DONE")) {
                                    Thread.currentThread().interrupt();
                                    scheduler_counter1++;
                                    String bak_thread_status_msg="<html><font color=#0565ff>Бэкап БД завершен</font>";
                                    status.setText(bak_thread_status_msg);
                                    status.paintImmediately(status.getVisibleRect());
                                    if (scheduler_counter1 == 2)
                                    {
                                        bak_thread_status = "EXECUTED";
                                        //"THREAD IS STOPPED NOTHING TO DO"
                                        scheduled_bak_watch.shutdown();
                                        progressbar1.setValue(100);
                                        restore_db();
                                    }
                                }
                                if (bak_thread_status != null && bak_thread_status.equals("WORKING")) {
                                    String query1="SELECT  r.session_id AS [Session_Id]\n" +
                        "             ,CONVERT(NUMERIC(6, 0), r.percent_complete) AS [Complete]\n" +
                        "             ,CONVERT(VARCHAR(1000), (\n" +
                        "             SELECT SUBSTRING(TEXT, r.statement_start_offset / 2,1000)  \n" +
                        "             FROM sys.dm_exec_sql_text(sql_handle)\n" +
                        "             ))as 'query'\n" +
                        "             FROM sys.dm_exec_requests r\n" +
                        "             WHERE   command like 'BACKUP%'";
                                    backup_progress = progress(query1);
                                    progressbar1.setValue(backup_progress);
                                }
                            }
                        },0,5, TimeUnit.SECONDS);
                    }
                    else {
                        progressbar1.setValue(100);
                        restore_db();
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
        source_list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                source_base = (String) source_list.getSelectedValue();
                db_space(source_base_size, source_base);
            }
        });
        target_list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                target_base = (String) target_list.getSelectedValue();
                db_space(target_base_size, target_base);
            }
        });
        backup_check.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if(e.getStateChange() == ItemEvent.SELECTED){
                    source_list.setEnabled(false);
                    db_space(source_base_size, last_backup());
                }
                else {
                    source_list.setEnabled(true);
                    db_space(source_base_size, (String) source_list.getSelectedValue());
                }
            }
        });
        source_search.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter();}

            private void filter() {
                DefaultListModel model = (DefaultListModel) source_list.getModel();
                model.clear();
                String s = source_search.getText();
                target_search.setText(s);
                for (Object o : source_buffer) {
                    if (o.toString().contains(s)) {
                        model.addElement(o.toString());
                    }
                }
                source_list.setModel(model);
            }
        });
        target_search.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter();}

            private void filter() {
                DefaultListModel model1 = (DefaultListModel) target_list.getModel();
                model1.clear();
                String s = target_search.getText();
                for (Object o : target_buffer) {
                    if (o.toString().contains(s)) {
                        model1.addElement(o.toString());
                    }
                }
                target_list.setModel(model1);
            }
        });

        ImageIcon img = new ImageIcon("conf/icon.png");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(img.getImage());
        frame.setMinimumSize(new Dimension(800, 600));
        frame.setPreferredSize(new Dimension(800, 600));
        frame.add(main);
        frame.pack();
        frame.setVisible(true);
        current_tasks.setVisible(false);
        backup_check.setVisible(false);
        current_bak.setVisible(false);
    }

    public static void main(String[] args){
        new Docker();
    }

}