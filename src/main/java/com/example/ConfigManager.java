package com.example;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理器 - 管理应用程序配置
 */
public class ConfigManager {
    
    private static final String CONFIG_FILE = "config.properties";
    
    private String inputDirectory;
    private String outputDirectory;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private String dbSchema;
    private String excludeDatabasePath;
    private Set<String> elementFilterKeywords = Collections.emptySet();
    
    public ConfigManager() {
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            
            if (!configFile.exists()) {
                // 创建默认配置
                createDefaultConfig();
                return;
            }
            
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }
            
            // 优先使用环境变量，然后是配置文件，最后是默认值
            inputDirectory = getConfigValue("INPUT_DIR", props.getProperty("input.directory", "./input"));
            outputDirectory = getConfigValue("OUTPUT_DIR", props.getProperty("output.directory", "./output"));
            
            // 数据库配置
            dbUrl = getConfigValue("DB_URL", props.getProperty("database.url", "jdbc:postgresql://localhost:5432/postgres"));
            dbUser = getConfigValue("DB_USER", props.getProperty("database.user", "postgres"));
            dbPassword = getConfigValue("DB_PASSWORD", props.getProperty("database.password", "geovis123"));
            dbSchema = getConfigValue("DB_SCHEMA", props.getProperty("database.schema", "public"));
            
            // 排除数据库路径配置
            excludeDatabasePath = getConfigValue("EXCLUDE_DATABASE_PATH", props.getProperty("exclude.database.path", ""));
            
            String filterRaw = getConfigValue("ELEMENT_FILTER_KEYWORDS", props.getProperty("element.filter.keywords", ""));
            elementFilterKeywords = parseElementFilterKeywords(filterRaw);
            
            System.out.println("配置文件加载成功: " + CONFIG_FILE);
            System.out.println("输入目录: " + inputDirectory);
            System.out.println("输出目录: " + outputDirectory);
            System.out.println("数据库URL: " + dbUrl);
            System.out.println("排除数据库路径: " + (excludeDatabasePath.isEmpty() ? "未配置" : excludeDatabasePath));
            System.out.println("Ԫ�ز����б�: " + (elementFilterKeywords.isEmpty() ? "δ����" : String.join(",", elementFilterKeywords)));
            
        } catch (IOException e) {
            System.err.println("加载配置文件失败，使用默认配置: " + e.getMessage());
            createDefaultConfig();
        }
    }
    
    /**
     * 获取配置值，优先使用环境变量
     */
    private String getConfigValue(String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        return envValue != null ? envValue : defaultValue;
    }
    
    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        inputDirectory = "./input";
        outputDirectory = "./output";
        dbUrl = "jdbc:postgresql://localhost:5432/postgres";
        dbUser = "postgres";
        dbPassword = "geovis123";
        dbSchema = "public";
        excludeDatabasePath = "";
        elementFilterKeywords = Collections.emptySet();
        
        saveConfig();
    }
    
    /**
     * 保存配置到文件
     */
    private void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("input.directory", inputDirectory);
            props.setProperty("output.directory", outputDirectory);
            props.setProperty("database.url", dbUrl);
            props.setProperty("database.user", dbUser);
            props.setProperty("database.password", dbPassword);
            props.setProperty("database.schema", dbSchema);
            props.setProperty("exclude.database.path", excludeDatabasePath);
            props.setProperty("element.filter.keywords", elementFilterKeywords.isEmpty() ? "" : String.join(",", elementFilterKeywords));
            
            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                props.store(fos, "NC File Processor Configuration");
            }
            
            System.out.println("配置文件已保存: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }
    
    public String getInputDirectory() {
        return inputDirectory;
    }
    
    public String getOutputDirectory() {
        return outputDirectory;
    }
    
    public String getDbUrl() {
        return dbUrl;
    }
    
    public String getDbUser() {
        return dbUser;
    }
    
    public String getDbPassword() {
        return dbPassword;
    }
    
    public String getDbSchema() {
        return dbSchema;
    }
    
    public String getExcludeDatabasePath() {
        return excludeDatabasePath;
    }
    
    public Set<String> getElementFilterKeywords() {
        return elementFilterKeywords;
    }
    
    /**
     * 检查指定路径是否应该排除数据库写入
     */
    public boolean isExcludedFromDatabase(String filePath) {
        if (excludeDatabasePath == null || excludeDatabasePath.trim().isEmpty()) {
            return false;
        }
        
        try {
            java.nio.file.Path configPath = java.nio.file.Paths.get(excludeDatabasePath).toAbsolutePath().normalize();
            java.nio.file.Path checkPath = java.nio.file.Paths.get(filePath).toAbsolutePath().normalize();
            
            // 检查文件路径是否以排除路径开头
            return checkPath.startsWith(configPath);
        } catch (Exception e) {
            System.err.println("检查排除路径失败: " + e.getMessage());
            return false;
        }
    }

    private Set<String> parseElementFilterKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(rawKeywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
