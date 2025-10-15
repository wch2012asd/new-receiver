package com.example;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库管理器 - 管理文件转换记录
 */
public class DatabaseManager {
    
    private final ConfigManager configManager;
    private Connection connection;
    private final SnowflakeIdGenerator idGenerator;
    
    public DatabaseManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.idGenerator = new SnowflakeIdGenerator();
        try {
            // 加载PostgreSQL驱动
            Class.forName("org.postgresql.Driver");
            System.out.println("PostgreSQL驱动加载成功");
            
            // 建立数据库连接
            System.out.println("尝试连接数据库: " + configManager.getDbUrl());
            connection = DriverManager.getConnection(
                configManager.getDbUrl(), 
                configManager.getDbUser(), 
                configManager.getDbPassword()
            );
            
            if (connection != null && !connection.isClosed()) {
                System.out.println("数据库连接成功");
                
                // 创建表
                createTableIfNotExists();
            } else {
                System.err.println("数据库连接失败：连接为null或已关闭");
            }
            
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL驱动未找到: " + e.getMessage());
            System.err.println("请确保PostgreSQL JDBC驱动在classpath中");
        } catch (SQLException e) {
            System.err.println("数据库连接失败: " + e.getMessage());
            System.err.println("请检查数据库服务是否运行，以及连接参数是否正确");
        } catch (Exception e) {
            System.err.println("初始化数据库管理器时发生未知错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建文件转换记录表
     */
    private void createTableIfNotExists() throws SQLException {
        // 先检查表是否存在
        boolean fileRecordsTableExists = checkTableExists("file_conversion_records");
        
        if (fileRecordsTableExists) {
            System.out.println("file_conversion_records表已存在，跳过创建");
        } else {
            System.out.println("file_conversion_records表不存在，开始创建...");
            
            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + configManager.getDbSchema() + ".file_conversion_records (" +
                "id SERIAL PRIMARY KEY, " +
                "folder_name VARCHAR(255) NOT NULL, " +
                "file_name VARCHAR(255) NOT NULL, " +
                "file_path TEXT NOT NULL, " +
                "output_path TEXT NOT NULL, " +
                "file_size BIGINT, " +
                "conversion_time TIMESTAMP DEFAULT DATE_TRUNC('second', CURRENT_TIMESTAMP), " +
                "status VARCHAR(50) DEFAULT 'SUCCESS', " +
                "UNIQUE(folder_name, file_name)" +
                ")";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
                System.out.println("file_conversion_records表创建成功");
            }
        }
        
        // 创建data_png_table表
        createDataPngTableIfNotExists();
    }
    
    /**
     * 创建data_png_table表
     */
    private void createDataPngTableIfNotExists() throws SQLException {
        // 先检查表是否存在
        boolean dataPngTableExists = checkTableExists("data_png_table");
        
        if (dataPngTableExists) {
            System.out.println("data_png_table表已存在，跳过创建");
        } else {
            System.out.println("data_png_table表不存在，开始创建...");
            
            String createDataPngTableSQL = "CREATE TABLE IF NOT EXISTS " + configManager.getDbSchema() + ".data_png_table (" +
                "id BIGINT PRIMARY KEY, " +
                "ele_name VARCHAR(50), " +
                "data_time TIMESTAMP, " +
                "level INTEGER, " +
                "file_name VARCHAR(255), " +
                "file_path TEXT, " +
                "png_path TEXT, " +
                "json_path TEXT, " +
                "png_relative_path TEXT, " +
                "json_relative_path TEXT, " +
                "create_time TIMESTAMP DEFAULT DATE_TRUNC('second', CURRENT_TIMESTAMP), " +
                "task_id VARCHAR(255), " +
                "timer INTEGER, " +
                "date INTEGER" +
                ")";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createDataPngTableSQL);
                System.out.println("data_png_table表创建成功");
            }
        }
        
        // 为data_png_table表创建索引以提高查询性能
        createDataPngTableIndexes();
    }
    
    /**
     * 为data_png_table表创建索引
     */
    private void createDataPngTableIndexes() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 为date字段创建索引（数据日期的主要查询字段）
            String createDateIndex = "CREATE INDEX IF NOT EXISTS idx_data_png_table_date ON " + 
                configManager.getDbSchema() + ".data_png_table(date)";
            stmt.execute(createDateIndex);
            
            // 为data_time字段创建索引（数据时间的查询字段）
            String createDataTimeIndex = "CREATE INDEX IF NOT EXISTS idx_data_png_table_data_time ON " + 
                configManager.getDbSchema() + ".data_png_table(data_time)";
            stmt.execute(createDataTimeIndex);
            
            // 为ele_name字段创建索引（元素名的查询字段）
            String createEleNameIndex = "CREATE INDEX IF NOT EXISTS idx_data_png_table_ele_name ON " + 
                configManager.getDbSchema() + ".data_png_table(ele_name)";
            stmt.execute(createEleNameIndex);
            
            // 为level字段创建索引（层级的查询字段）
            String createLevelIndex = "CREATE INDEX IF NOT EXISTS idx_data_png_table_level ON " + 
                configManager.getDbSchema() + ".data_png_table(level)";
            stmt.execute(createLevelIndex);
            
            // 为task_id字段创建索引（任务ID的查询字段）
            String createTaskIdIndex = "CREATE INDEX IF NOT EXISTS idx_data_png_table_task_id ON " + 
                configManager.getDbSchema() + ".data_png_table(task_id)";
            stmt.execute(createTaskIdIndex);
            
            // 创建复合索引：元素名 + 数据日期 + 层级（常用的组合查询）
            String createCompositeIndex = "CREATE INDEX IF NOT EXISTS idx_data_png_table_composite ON " + 
                configManager.getDbSchema() + ".data_png_table(ele_name, date, level)";
            stmt.execute(createCompositeIndex);
            
            System.out.println("data_png_table表的索引已创建或已存在");
        }
    }
    
    /**
     * 检查指定的表是否存在
     * @param tableName 表名
     * @return 表是否存在
     */
    private boolean checkTableExists(String tableName) {
        if (!isConnectionValid()) {
            System.err.println("数据库连接不可用，无法检查表存在性");
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, configManager.getDbSchema());
            pstmt.setString(2, tableName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    boolean exists = rs.getInt(1) > 0;
                    System.out.println("表存在性检查: " + configManager.getDbSchema() + "." + tableName + " -> " + (exists ? "存在" : "不存在"));
                    return exists;
                }
            }
        } catch (SQLException e) {
            System.err.println("检查表存在性失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 检查数据库连接是否可用
     */
    private boolean isConnectionValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * 检查文件夹是否已处理过
     */
    public boolean isFolderProcessed(String folderName) {
        if (!isConnectionValid()) {
            System.err.println("数据库连接不可用，无法检查文件夹状态");
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM " + configManager.getDbSchema() + ".file_conversion_records WHERE folder_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("检查文件夹状态失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 检查特定文件是否已处理过
     */
    public boolean isFileProcessed(String folderName, String fileName) {
        if (!isConnectionValid()) {
            System.err.println("数据库连接不可用，无法检查文件状态");
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM " + configManager.getDbSchema() + ".file_conversion_records WHERE folder_name = ? AND file_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderName);
            pstmt.setString(2, fileName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("检查文件状态失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 记录文件转换结果
     */
    public void recordFileConversion(String folderName, String fileName, String filePath, 
                                   String outputPath, long fileSize, String status) {
        if (!isConnectionValid()) {
            System.err.println("数据库连接不可用，无法记录文件转换结果");
            return;
        }
        
        String sql = "INSERT INTO " + configManager.getDbSchema() + ".file_conversion_records " +
            "(folder_name, file_name, file_path, output_path, file_size, status) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (folder_name, file_name) " +
            "DO UPDATE SET " +
            "file_path = EXCLUDED.file_path, " +
            "output_path = EXCLUDED.output_path, " +
            "file_size = EXCLUDED.file_size, " +
            "conversion_time = DATE_TRUNC('second', CURRENT_TIMESTAMP), " +
            "status = EXCLUDED.status";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderName);
            pstmt.setString(2, fileName);
            pstmt.setString(3, filePath);
            pstmt.setString(4, outputPath);
            pstmt.setLong(5, fileSize);
            pstmt.setString(6, status);
            
            pstmt.executeUpdate();
            System.out.println("文件转换记录已保存: " + folderName + "/" + fileName);
            
        } catch (SQLException e) {
            System.err.println("保存文件转换记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 向data_png_table表中插入NC转换结果
     * @return 插入记录的雪花ID，如果插入失败返回null
     */
    public Long insertDataPngRecord(String eleName, Long dataTime, Integer level, String fileName, 
                                    String filePath, String pngPath, String jsonPath, 
                                    String pngRelativePath, String jsonRelativePath, 
                                    String taskId, Integer timer, Integer date) {
        if (!isConnectionValid()) {
            System.err.println("数据库连接不可用，无法插入data_png_table记录");
            return null;
        }
        
        // 生成雪花ID
        Long snowflakeId = idGenerator.nextId();
        
        String sql = "INSERT INTO " + configManager.getDbSchema() + ".data_png_table " +
            "(id, ele_name, data_time, level, file_name, file_path, png_path, json_path, " +
            "png_relative_path, json_relative_path, task_id, timer, date, create_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, DATE_TRUNC('second', CURRENT_TIMESTAMP))";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, snowflakeId);
            pstmt.setString(2, eleName);
            if (dataTime != null) {
                // 将时间精度调整为只到秒级，去掉毫秒部分
                long truncatedTime = truncateToSeconds(dataTime);
                Timestamp timestamp = new Timestamp(truncatedTime);
                pstmt.setTimestamp(3, timestamp);
            } else {
                pstmt.setNull(3, Types.TIMESTAMP);
            }
            if (level != null) {
                pstmt.setInt(4, level);
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }
            pstmt.setString(5, fileName);
            pstmt.setString(6, filePath);
            pstmt.setString(7, pngPath);
            pstmt.setString(8, jsonPath);
            pstmt.setString(9, pngRelativePath);
            pstmt.setString(10, jsonRelativePath);
            if (taskId != null && !taskId.trim().isEmpty()) {
                pstmt.setString(11, taskId.trim());
            } else {
                pstmt.setNull(11, Types.VARCHAR);
            }
            if (timer != null) {
                pstmt.setInt(12, timer);
            } else {
                pstmt.setNull(12, Types.INTEGER);
            }
            if (date != null) {
                pstmt.setInt(13, date);
            } else {
                pstmt.setNull(13, Types.INTEGER);
            }
            
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // 输出时间精度处理信息
                if (dataTime != null) {
                    long truncatedTime = truncateToSeconds(dataTime);
                    // 格式化时间为简洁格式显示（不带时区）
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime dateTime = LocalDateTime.ofEpochSecond(truncatedTime / 1000, 0, 
                        java.time.ZoneOffset.ofHours(8)); // 使用+08时区但不显示
                    String formattedTime = dateTime.format(formatter);
                    
                    if (dataTime != truncatedTime) {
                        System.out.println("data_png_table记录已插入 (雪花ID: " + snowflakeId + "): " + eleName + " - " + fileName + " - " + timer + " (时间: " + formattedTime + ", 精度已调整为秒级)");
                    } else {
                        System.out.println("data_png_table记录已插入 (雪花ID: " + snowflakeId + "): " + eleName + " - " + fileName + " - " + timer + " (时间: " + formattedTime + ")");
                    }
                } else {
                    System.out.println("data_png_table记录已插入 (雪花ID: " + snowflakeId + "): " + eleName + " - " + fileName + " - " + timer + " (时间: null)");
                }
                
                return snowflakeId;
            } else {
                System.err.println("插入data_png_table记录失败，影响行数: " + rowsAffected);
                return null;
            }
            
        } catch (SQLException e) {
            System.err.println("插入data_png_table记录失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取文件夹的处理记录
     */
    public List<FileConversionRecord> getFolderRecords(String folderName) {
        List<FileConversionRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM " + configManager.getDbSchema() + ".file_conversion_records WHERE folder_name = ? ORDER BY conversion_time DESC";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    FileConversionRecord record = new FileConversionRecord();
                    record.setId(rs.getLong("id"));
                    record.setFolderName(rs.getString("folder_name"));
                    record.setFileName(rs.getString("file_name"));
                    record.setFilePath(rs.getString("file_path"));
                    record.setOutputPath(rs.getString("output_path"));
                    record.setFileSize(rs.getLong("file_size"));
                    record.setConversionTime(rs.getTimestamp("conversion_time").toLocalDateTime());
                    record.setStatus(rs.getString("status"));
                    
                    records.add(record);
                }
            }
        } catch (SQLException e) {
            System.err.println("获取文件夹记录失败: " + e.getMessage());
        }
        
        return records;
    }
    
    /**
     * 获取所有处理过的文件夹名称
     */
    public List<String> getProcessedFolders() {
        List<String> folders = new ArrayList<>();
        String sql = "SELECT DISTINCT folder_name FROM " + configManager.getDbSchema() + ".file_conversion_records ORDER BY folder_name";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                folders.add(rs.getString("folder_name"));
            }
        } catch (SQLException e) {
            System.err.println("获取已处理文件夹列表失败: " + e.getMessage());
        }
        
        return folders;
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("数据库连接已关闭");
            }
        } catch (SQLException e) {
            System.err.println("关闭数据库连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 文件转换记录实体类
     */
    public static class FileConversionRecord {
        private Long id;
        private String folderName;
        private String fileName;
        private String filePath;
        private String outputPath;
        private Long fileSize;
        private LocalDateTime conversionTime;
        private String status;
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getFolderName() { return folderName; }
        public void setFolderName(String folderName) { this.folderName = folderName; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public String getOutputPath() { return outputPath; }
        public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
        
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        
        public LocalDateTime getConversionTime() { return conversionTime; }
        public void setConversionTime(LocalDateTime conversionTime) { this.conversionTime = conversionTime; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    /**
     * 将时间戳精度调整为只到秒级，去掉毫秒部分
     * @param timestampMillis 毫秒级时间戳
     * @return 秒级时间戳（毫秒单位，但毫秒部分为0）
     */
    private long truncateToSeconds(long timestampMillis) {
        return (timestampMillis / 1000) * 1000;
    }
}
