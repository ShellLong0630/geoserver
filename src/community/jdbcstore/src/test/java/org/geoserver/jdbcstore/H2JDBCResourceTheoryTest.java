package org.geoserver.jdbcstore;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import org.geoserver.jdbcconfig.internal.Util;
import org.geoserver.platform.resource.FileSystemResourceStore;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.ResourceTheoryTest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.rules.TemporaryFolder;

public class H2JDBCResourceTheoryTest extends ResourceTheoryTest {

    JDBCResourceStore store;
    Connection conn;
    
    @DataPoints
    public static String[] testPaths() {
        return new String[]{"FileA","FileB", "DirC", "DirC/FileD", "DirE", "UndefF", "DirC/UndefF", "DirE/UndefF", "DirE/UndefG/UndefH/UndefI"};
    }

    @Override
    protected Resource getResource(String path) throws Exception{
        return store.get(path);
    }
    
    private int addFile(String name, int parent, byte[] content, PreparedStatement insert) throws Exception {
        insert.setString(1, name);
        insert.setInt(2, parent);
        insert.setBytes(3, content);
        insert.execute();
        ResultSet rs = insert.getGeneratedKeys();
        if(rs.next()) {
            return rs.getInt(1);
        } else {
            throw new IllegalStateException("Could not add test file "+name);
        }
    }
    private int addDir(String name, int parent, PreparedStatement insert) throws Exception  {
        insert.setString(1, name);
        insert.setInt(2, parent);
        insert.setBytes(3, null);
        insert.execute();
        ResultSet rs = insert.getGeneratedKeys();
        if(rs.next()) {
            return rs.getInt(1);
        } else {
            throw new IllegalStateException("Could not add test directory "+name);
        }
    }
    
    @Before
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test");
        conn = ds.getConnection();
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(ds);
        
        Util.runScript(JDBCResourceStore.class.getResource("init.h2.sql"), template.getJdbcOperations(), null);
        
        PreparedStatement insert = conn.prepareStatement("INSERT INTO resource (name, parent, content) VALUES (?, ?, ?)");
        
        try{
            addFile("FileA", 0, "FileA Contents".getBytes(), insert);
            addFile("FileB", 0, "FileB Contents".getBytes(), insert);
            int c = addDir("DirC", 0, insert);
            addFile("FileD", c, "FileD Contents".getBytes(), insert);
            addDir("DirE", 0, insert);
        } finally {
            insert.close();
        }
        
        printTable();
        
        store = new JDBCResourceStore(ds, "TEST", "INFORMATION_SCHEMA", "init.h2.sql");
    }
    
    @After
    public void tearDown() throws Exception {
        conn.close();
    }

    void printTable() throws Exception{
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * from resource;");
        System.out.println();
        System.out.println("---");
        System.out.printf("\t%s\t%s\t%s\t%s\t%s\n", "oid", "name", "parent", "last_modified", "directory");
        System.out.println("---");
        try {
            while(rs.next()) {
                System.out.printf("\t%d\t%s\t%d\t%s\t%s\n", rs.getInt("oid"), rs.getString("name"), getInt(rs,"parent"), rs.getTimestamp("last_modified"), rs.getBlob("content")==null);
            }
        } finally {
            rs.close();
        }
    }
    
    Integer getInt(ResultSet rs, String column) throws Exception {
        int i = rs.getInt(column);
        if(rs.wasNull()) return null;
        return i;
    }
}
