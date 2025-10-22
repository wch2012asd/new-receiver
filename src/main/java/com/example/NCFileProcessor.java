package com.example;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * NC文件处理器 - 监控目录变化并将NC文件转换为红黑图
 */
public class NCFileProcessor {
    
    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;
    private final DatabaseManager databaseManager;
    
    public NCFileProcessor() {
        this.configManager = new ConfigManager();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // 尝试初始化数据库管理器
        DatabaseManager tempDatabaseManager = null;
        try {
            tempDatabaseManager = new DatabaseManager(configManager);
        } catch (Exception e) {
            System.err.println("数据库初始化失败，程序将在无数据库模式下运行: " + e.getMessage());
        }
        this.databaseManager = tempDatabaseManager;
    }
    
    /**
     * 启动文件监控服务
     */
    public void start() {
        System.out.println("启动NC文件处理器...");
        System.out.println("输入目录: " + configManager.getInputDirectory());
        System.out.println("输出目录: " + configManager.getOutputDirectory());
        
        // 每3秒检查一次目录变化
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkDirectoryChanges();
            } catch (Exception e) {
                System.err.println("定时任务执行异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 3, TimeUnit.SECONDS);
        
        System.out.println("NC文件处理器已启动，开始监控...");
    }
    
    /**
     * 停止服务
     */
    public void stop() {
        System.out.println("停止NC文件处理器...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭数据库连接（如果存在）
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
    
    /**
     * 检查目录变化
     */
    private void checkDirectoryChanges() {
        try {
            Path inputDir = Paths.get(configManager.getInputDirectory());
            
            if (!Files.exists(inputDir)) {
                System.out.println("输入目录不存在: " + inputDir);
                return;
            }
            
            // 遍历输入目录下的所有子文件夹
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, Files::isDirectory)) {
                for (Path folder : stream) {
                    String folderName = folder.getFileName().toString();
                    
                    // 检查数据库中是否已处理过该文件夹（如果数据库可用）
                    boolean shouldProcess = (databaseManager == null) || !databaseManager.isFolderProcessed(folderName);

//                    System.out.println("检查文件夹: " + folderName + " (数据库可用: " + (databaseManager != null) + ", 需要处理: " + shouldProcess + ")");
                    
                    if (shouldProcess) {
                        System.out.println("发现新文件夹: " + folderName);
                        System.out.println("开始处理文件夹: " + folderName);
                        
                        // 直接处理文件夹中的NC文件（同步处理）
                        processNCFilesInFolder(folder);
                        
                        System.out.println("完成处理文件夹: " + folderName);
                    } else {
                        // 检查是否有新的NC文件需要处理
                        checkForNewFilesInFolder(folder);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("检查目录变化时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理文件夹中的NC文件（递归查找所有子目录）
     */
    private void processNCFilesInFolder(Path folder) throws Exception {
        System.out.println("  递归查找NC文件: " + folder.getFileName());
        
        // 递归查找所有NC文件
        List<Path> ncFiles = findNCFilesRecursively(folder);
        
        if (ncFiles.isEmpty()) {
            System.out.println("  文件夹及其子目录中未找到NC文件: " + folder.getFileName());
            // 显示目录结构以便调试
            displayDirectoryStructure(folder, 0);
        } else {
            System.out.println("  总共找到 " + ncFiles.size() + " 个NC文件");
            
            for (Path ncFile : ncFiles) {
                // 计算相对路径用于显示
                Path relativePath = folder.relativize(ncFile);

                
                try {
                    processNCFile(ncFile);
                } catch (Exception e) {
                    System.err.println("  处理NC文件失败: " + relativePath + " - " + e.getMessage());
                    
                    // 记录失败的转换（使用数据库管理器，如果可用且未被排除）
                    boolean excludeFromDatabase = configManager.isExcludedFromDatabase(ncFile.toString());
                    if (!excludeFromDatabase && databaseManager != null) {
                        String folderName = ncFile.getParent().getFileName().toString();
                        String fileName = ncFile.getFileName().toString();
                        long fileSize = 0;
                        try {
                            fileSize = Files.size(ncFile);
                        } catch (IOException ex) {
                            // 忽略文件大小获取失败
                        }
                        
                        databaseManager.recordFileConversion(folderName, fileName, 
                            ncFile.toString(), "", fileSize, "FAILED: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * 递归查找目录中的所有NC文件
     */
    private List<Path> findNCFilesRecursively(Path startPath) throws IOException {
        List<Path> ncFiles = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(startPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    // 递归查找子目录
                    ncFiles.addAll(findNCFilesRecursively(entry));
                } else if (Files.isRegularFile(entry) && entry.getFileName().toString().toLowerCase().endsWith(".nc")) {
                    // 找到NC文件
                    ncFiles.add(entry);
                }
            }
        }
        
        return ncFiles;
    }
    
    /**
     * 显示目录结构用于调试
     */
    private void displayDirectoryStructure(Path startPath, int depth) {
        if (depth > 3) { // 限制显示深度避免输出过多
            return;
        }
        
        // 创建缩进字符串（Java 8兼容版本）
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i <= depth; i++) {
            indentBuilder.append("    ");
        }
        String indent = indentBuilder.toString();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(startPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    System.out.println(indent + "[目录] " + entry.getFileName());
                    displayDirectoryStructure(entry, depth + 1);
                } else {
                    System.out.println(indent + "[文件] " + entry.getFileName());
                }
            }
        } catch (IOException e) {
            System.err.println(indent + "无法读取目录: " + e.getMessage());
        }
    }
    
    /**
     * 检查已处理文件夹中的新文件（递归查找所有子目录）
     */
    private void checkForNewFilesInFolder(Path folder) throws Exception {
        String folderName = folder.getFileName().toString();
       //System.out.println("  检查已处理文件夹中的新文件: " + folderName);
        
        // 递归查找所有NC文件
        List<Path> ncFiles = findNCFilesRecursively(folder);
        
        if (ncFiles.isEmpty()) {
            System.out.println("  文件夹及其子目录中未找到NC文件: " + folderName);
        } else {
            //System.out.println("  总共找到 " + ncFiles.size() + " 个NC文件需要检查");
            
            for (Path ncFile : ncFiles) {
                // 获取直接父目录的名称（作为数据库中的folderName）
                String directParentFolderName = ncFile.getParent().getFileName().toString();
                String fileName = ncFile.getFileName().toString();
                
                // 计算相对路径用于显示
                Path relativePath = folder.relativize(ncFile);
                //System.out.println("    检查文件: " + relativePath);
                
                // 检查该文件是否应该排除数据库写入
                boolean excludeFromDatabase = configManager.isExcludedFromDatabase(ncFile.toString());
                
                // 检查该文件是否已处理过（如果数据库可用且未被排除）
                boolean shouldProcessFile = excludeFromDatabase || (databaseManager == null) || !databaseManager.isFileProcessed(directParentFolderName, fileName);
                if (shouldProcessFile){
                    System.out.println("    文件 " + relativePath + " 是否需要处理: " + shouldProcessFile + 
                                 (excludeFromDatabase ? " (排除数据库)" : ""));
                }
                
                
                if (shouldProcessFile) {
                    System.out.println("  发现新文件: " + relativePath + " (在已处理文件夹: " + folderName + ")");
                    processNCFile(ncFile);
                } else {
                    //System.out.println("  跳过已处理文件: " + relativePath + " (在文件夹: " + folderName + ")");
                }
            }
        }
    }
    
    /**
     * 处理单个NC文件，转换为红黑图
     */
    private void processNCFile(Path ncFilePath) throws IOException {
        // 计算文件相对于输入根目录的完整路径
        Path inputRoot = Paths.get(configManager.getInputDirectory());
        Path relativeFilePath = inputRoot.relativize(ncFilePath);
        
        boolean excludeFromDatabase = configManager.isExcludedFromDatabase(ncFilePath.toString());
        if (excludeFromDatabase) {
            System.out.println("    该文件位于排除目录，仅执行转换，不写入数据库: " + relativeFilePath);
        }
        
        String folderName = ncFilePath.getParent().getFileName().toString();
        String fileName = ncFilePath.getFileName().toString();
        
        if (!excludeFromDatabase && databaseManager != null && databaseManager.isFileProcessed(folderName, fileName)) {
            return;
        }
        
        System.out.println("    开始处理NC文件: " + relativeFilePath);
        


        
        try {
            // 计算输出目录路径
            String outputDirPath = calculateOutputDirectory(ncFilePath);
            
            // 计算输出目录相对于输出根目录的路径用于显示
            Path outputRoot = Paths.get(configManager.getOutputDirectory());
            Path relativeOutputPath = outputRoot.relativize(Paths.get(outputDirPath));
            
            System.out.println("    输出目录: " + relativeOutputPath);
            
            // 确保输出目录存在
            Files.createDirectories(Paths.get(outputDirPath));
            System.out.println("    输出目录已创建");
            
            // 使用NcToPngUtils转换NC文件
            System.out.println("    开始调用NcToPngUtils.ncToPng...");
            List<NcBeanModel> results = NcToPngUtils.ncToPng(ncFilePath.toString(), outputDirPath, configManager.getElementFilterKeywords());
            System.out.println("    NcToPngUtils.ncToPng调用完成，结果数量: " + (results != null ? results.size() : "null"));
            
            // 记录转换结果到数据库（如果可用且未被排除）
            if (!excludeFromDatabase && databaseManager != null) {
                long fileSize = Files.size(ncFilePath);
                
                if (results != null && !results.isEmpty()) {
                    // 转换成功
                    String outputInfo = "Generated " + results.size() + " images";
                    databaseManager.recordFileConversion(folderName, fileName, 
                        ncFilePath.toString(), outputDirPath, fileSize, "SUCCESS: " + outputInfo);
                    
                    System.out.println("    成功转换NC文件，生成了 " + results.size() + " 个图像");
                    System.out.println("    文件路径映射: " + relativeFilePath + " -> " + relativeOutputPath);
                    
                    // 将每个转换结果插入data_png_table表
                    insertResultsToDataPngTable(results, ncFilePath, fileName, excludeFromDatabase);
                    
                    for (NcBeanModel result : results) {
                        System.out.println("      - " + result.toString());
                    }
                } else {
                    // 转换失败或无结果
                    databaseManager.recordFileConversion(folderName, fileName, 
                        ncFilePath.toString(), outputDirPath, fileSize, "SUCCESS: No variables found");
                    System.out.println("    NC文件转换完成，但未找到可转换的变量");
                    System.out.println("    文件路径映射: " + relativeFilePath + " -> " + relativeOutputPath);
                }
            } else if (excludeFromDatabase) {
                // 被排除的文件仍然显示转换结果，但不写入数据库
                if (results != null && !results.isEmpty()) {
                    System.out.println("    成功转换NC文件（未写入数据库），生成了 " + results.size() + " 个图像");
                    System.out.println("    文件路径映射: " + relativeFilePath + " -> " + relativeOutputPath);
                    
                    for (NcBeanModel result : results) {
                        System.out.println("      - " + result.toString());
                    }
                } else {
                    System.out.println("    NC文件转换完成（未写入数据库），但未找到可转换的变量");
                    System.out.println("    文件路径映射: " + relativeFilePath + " -> " + relativeOutputPath);
                }
            }
            
        } catch (Exception e) {
            System.err.println("    转换NC文件失败: " + e.getMessage());
            
            // 记录失败到数据库（如果可用且未被排除）
            if (!excludeFromDatabase && databaseManager != null) {
                long fileSize = 0;
                try {
                    fileSize = Files.size(ncFilePath);
                } catch (IOException ex) {
                    // 忽略文件大小获取失败
                }
                
                databaseManager.recordFileConversion(folderName, fileName, 
                    ncFilePath.toString(), "", fileSize, "FAILED: " + e.getMessage());
            }
            
            throw new IOException("NC文件转换失败", e);
        }
    }
    
    /**
     * 将转换结果插入data_png_table表
     */
    private void insertResultsToDataPngTable(List<NcBeanModel> results, Path ncFilePath, String fileName, boolean excludeFromDatabase) {
        if (excludeFromDatabase || databaseManager == null || results == null || results.isEmpty()) {
            return;
        }

        String eleName = extractElementName(fileName);
        FileDateTimeInfo fileDateTimeInfo = extractDateTimeFromFileName(fileName);
        String date = normalizeString(fileDateTimeInfo.getDate());
        String hourFromFileName = normalizeString(fileDateTimeInfo.getHour());

        if (date == null) {
            System.out.println("    警告：无法从文件名解析到日期，尝试使用备用方案，文件名：" + fileName);

            if (results != null) {
                for (NcBeanModel result : results) {
                    if (result.getTime() != null) {
                        try {
                            java.time.Instant instant = java.time.Instant.ofEpochMilli(result.getTime());
                            java.time.LocalDate dateFromTime = instant.atZone(java.time.ZoneId.of("UTC")).toLocalDate();
                            date = dateFromTime.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                            System.out.println("    备用方案1：根据结果时间解析到的日期：" + date);
                            break;
                        } catch (Exception e) {
                            System.out.println("    备用方案1失败：" + e.getMessage());
                        }
                    }
                }
            }

            if (date == null && ncFilePath != null) {
                try {
                    java.nio.file.attribute.FileTime lastModified = java.nio.file.Files.getLastModifiedTime(ncFilePath);
                    java.time.LocalDate dateFromFile = lastModified.toInstant().atZone(java.time.ZoneId.of("UTC")).toLocalDate();
                    date = dateFromFile.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                    System.out.println("    备用方案2：根据文件修改时间解析到的日期：" + date);
                } catch (Exception e) {
                    System.out.println("    备用方案2失败：" + e.getMessage());
                }
            }

            if (date == null && ncFilePath != null) {
                try {
                    String fullPath = ncFilePath.toString();
                    java.util.regex.Pattern pathDatePattern = java.util.regex.Pattern.compile("(\\d{8})");
                    java.util.regex.Matcher pathMatcher = pathDatePattern.matcher(fullPath);

                    while (pathMatcher.find()) {
                        String candidate = pathMatcher.group(1);
                        if (isValidDateString(candidate)) {
                            date = candidate;
                            System.out.println("    备用方案3：在路径中找到的日期：" + date);
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("    备用方案3失败：" + e.getMessage());
                }
            }

            if (date == null) {
                date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                System.out.println("    使用当前日期作为兜底值：" + date);
            }
        }

        Integer finalDateValue = safeParseInteger(date);

        if (eleName == null || eleName.trim().isEmpty()) {
            System.out.println("    警告：无法从文件名解析到元素名称，使用文件名兜底，文件名：" + fileName);

            String nameWithoutExt = fileName;
            if (fileName.toLowerCase().endsWith(".nc")) {
                nameWithoutExt = fileName.substring(0, fileName.length() - 3);
            }
            eleName = removeDateFromFileName(nameWithoutExt);
            if (eleName.length() > 20) {
                eleName = eleName.substring(0, 20);
            }
            eleName = eleName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            System.out.println("    兜底元素名称：" + eleName);
        }

        String taskId = buildTaskId(ncFilePath);

        for (NcBeanModel result : results) {
            try {
                String variableName = normalizeString(result.getVariableName());
                String finalEleName = sanitizeElementName(variableName, eleName);
                String pngPath = result.getPngPath();
                String level = result.getLevel();
                Integer levelValue = safeParseInteger(level);
                Long rawTime = result.getTime();

                String jsonPath = pngPath != null ? pngPath.replace(".png", ".json") : null;

                String pngRelativePath = calculateRelativePath(pngPath);
                String jsonRelativePath = calculateRelativePath(jsonPath);

                Integer finalTimer = safeParseInteger(hourFromFileName);
                if (hourFromFileName != null && finalTimer == null) {
                    System.out.println("    timer解析失败，使用默认值0：原始=" + hourFromFileName);
                }
                if (finalTimer == null) {
                    finalTimer = 0;
                }
                finalTimer = Math.min(23, Math.max(0, finalTimer));

                Long finalDataTime = buildDataTime(finalDateValue, finalTimer);

                String timerLog = finalTimer != null ? (finalTimer + "时") : "null";

                System.out.println("    准备写入数据库记录：");
                System.out.println("      元素：" + finalEleName);
                System.out.println("      日期：" + date);
                System.out.println("      层级：" + level);
                System.out.println("      文件：" + fileName);
                System.out.println("      Timer：" + timerLog);

                databaseManager.insertDataPngRecord(
                    finalEleName,
                    finalDataTime,
                    levelValue,
                    fileName,
                    ncFilePath.toString(),
                    pngPath,
                    jsonPath,
                    pngRelativePath,
                    jsonRelativePath,
                    taskId,
                    finalTimer,
                    finalDateValue
                );

            } catch (Exception e) {
                System.err.println("    写入data_png_table记录失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

/**
     * 从文件名中提取元素名
     * 支持多种不规范的文件命名格式，使用多种策略进行元素名提取
     */
    private String extractElementName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 移除文件后缀
            String nameWithoutExtension = fileName;
            if (fileName.toLowerCase().endsWith(".nc")) {
                nameWithoutExtension = fileName.substring(0, fileName.length() - 3);
            }
            
            // 移除日期部分，以便更好地提取元素名
            String nameWithoutDate = removeDateFromFileName(nameWithoutExtension);
            
            // 策祥1：尝试传统的ERA5格式（ERA5_ELEMENT_DATE）
            if (nameWithoutExtension.startsWith("ERA5_") && nameWithoutExtension.contains("_")) {
                String[] parts = nameWithoutExtension.split("_");
                if (parts.length >= 2) {
                    // 跳过ERA5和日期部分，取中间的元素名
                    for (int i = 1; i < parts.length; i++) {
                        if (!isDateLike(parts[i]) && !parts[i].isEmpty()) {
                            String elementName = parts[i].toLowerCase();
                            System.out.println("    元素名提取（策祥1-ERA5格式）: " + elementName + " 从 " + fileName);
                            return elementName;
                        }
                    }
                }
            }
            
            // 策祥2：使用常见的气象元素名匹配
            String[] commonElements = {
                "temperature", "temp", "t2m", "t",
                "humidity", "rh", "hum", "h",
                "pressure", "pres", "p", "slp", "msl",
                "wind", "u10", "v10", "ws", "wd",
                "precipitation", "prec", "rain", "pr",
                "lcc", "mcc", "hcc", "tcc", // 云量
                "vis", "visibility", // 能见度
                "sst", "sea_surface_temperature", // 海温
                "wh", "wave_height", // 浪高
                "cape", "cin", // 对流参数
                "geopotential", "z", "gh" // 位势高度
            };
            
            String lowerFileName = nameWithoutDate.toLowerCase();
            for (String element : commonElements) {
                if (lowerFileName.contains(element)) {
                    System.out.println("    元素名提取（策祥2-常见匹配）: " + element + " 从 " + fileName);
                    return element;
                }
            }
            
            // 策祥3：使用下划线分隔，取非日期的部分
            if (nameWithoutDate.contains("_")) {
                String[] parts = nameWithoutDate.split("_");
                for (String part : parts) {
                    if (!part.isEmpty() && !isDateLike(part) && !isNumeric(part)) {
                        // 过滤掉一些常见的非元素名部分
                        if (!part.toLowerCase().matches(".*(era5|ecmwf|gfs|data|file|nc).*")) {
                            String elementName = part.toLowerCase();
                            System.out.println("    元素名提取（策祥3-分隔符）: " + elementName + " 从 " + fileName);
                            return elementName;
                        }
                    }
                }
            }
            
            // 策祥4：使用中划线分隔
            if (nameWithoutDate.contains("-")) {
                String[] parts = nameWithoutDate.split("-");
                for (String part : parts) {
                    if (!part.isEmpty() && !isDateLike(part) && !isNumeric(part)) {
                        if (!part.toLowerCase().matches(".*(era5|ecmwf|gfs|data|file|nc).*")) {
                            String elementName = part.toLowerCase();
                            System.out.println("    元素名提取（策祥4-中划线）: " + elementName + " 从 " + fileName);
                            return elementName;
                        }
                    }
                }
            }
            
            // 策祥5：如果所有策略都失败，尝试使用整个文件名（去掉日期后）作为元素名
            if (nameWithoutDate.length() > 0 && nameWithoutDate.length() <= 20) {
                String elementName = nameWithoutDate.toLowerCase();
                System.out.println("    元素名提取（策祥5-整个名称）: " + elementName + " 从 " + fileName);
                return elementName;
            }
            
            System.out.println("    警告：无法从文件名提取元素名: " + fileName);
            return null;
            
        } catch (Exception e) {
            System.err.println("从文件名提取元素名失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 从文件名中移除日期部分
     */
    private String removeDateFromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return fileName;
        }
        
        String result = fileName;
        
        // 移除8位数字日期
        result = result.replaceAll("\\d{8}", "");
        // 移除6位数字日期
        result = result.replaceAll("\\d{6}", "");
        // 移除YYYY-MM-DD或YYYY/MM/DD格式的日期
        result = result.replaceAll("\\d{4}[\\-/]\\d{2}[\\-/]\\d{2}", "");
        // 清理多余的分隔符
        result = result.replaceAll("[_\\-]{2,}", "_");
        result = result.replaceAll("^[_\\-]+|[_\\-]+$", "");
        
        return result;
    }
    
    private String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildTaskId(Path ncFilePath) {
        if (ncFilePath == null) {
            return "unknown";
        }
        Path parent = ncFilePath.getParent();
        String raw = parent != null ? parent.getFileName().toString() : "root";
        raw = normalizeString(raw);
        if (raw == null || raw.isEmpty()) {
            raw = "unknown";
        }
        raw = raw.replaceAll("[^A-Za-z0-9_-]", "_");
        if (raw.length() > 100) {
            raw = raw.substring(0, 100);
        }
        return raw;
    }

    private String sanitizeElementName(String primary, String fallback) {
        String candidate = normalizeString(primary);
        if (candidate == null) {
            candidate = normalizeString(fallback);
        }
        if (candidate == null || candidate.isEmpty()) {
            candidate = "unknown";
        }
        candidate = candidate.replaceAll("[^A-Za-z0-9_-]", "_");
        if (candidate.length() > 50) {
            candidate = candidate.substring(0, 50);
        }
        return candidate;
    }

    private Long buildDataTime(Integer finalDateValue, Integer finalTimer) {
        if (finalDateValue == null) {
            return null;
        }
        try {
            String dateStr = String.format("%08d", finalDateValue);
            java.time.LocalDate parsedDate = java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            int hourValue = finalTimer != null ? Math.min(23, Math.max(0, finalTimer)) : 0;
            java.time.LocalTime parsedTime = java.time.LocalTime.of(hourValue, 0, 0);
            java.time.LocalDateTime localDateTime = java.time.LocalDateTime.of(parsedDate, parsedTime);
            System.out.println("    根据文件名生成的数据时间：" + localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return java.sql.Timestamp.valueOf(localDateTime).getTime();
        } catch (Exception e) {
            System.out.println("    根据文件名构造数据时间失败：" + e.getMessage());
            return null;
        }
    }

    private Integer safeParseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private FileDateTimeInfo extractDateTimeFromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return new FileDateTimeInfo(null, null);
        }
        
        String nameWithoutExtension = fileName;
        if (fileName.toLowerCase().endsWith(".nc")) {
            nameWithoutExtension = fileName.substring(0, fileName.length() - 3);
        }
        
        String date = null;
        String hour = null;
        
        java.util.regex.Pattern compactPattern = java.util.regex.Pattern.compile("(?<!\\d)(\\d{8})(\\d{2})(\\d{0,2})(?!\\d)");
        java.util.regex.Matcher compactMatcher = compactPattern.matcher(nameWithoutExtension);
        if (compactMatcher.find()) {
            String datePart = compactMatcher.group(1);
            String hourPart = compactMatcher.group(2);
            if (isValidDateString(datePart)) {
                date = datePart;
                if (isValidHourString(hourPart)) {
                    hour = hourPart;
                }
            }
        }
        
        if (date == null) {
            date = extractDateFromFileName(fileName);
        }
        
        if (hour == null) {
            java.util.regex.Pattern hourPattern = java.util.regex.Pattern.compile("(?:_|-)(\\d{2})(?!.*\\d)");
            java.util.regex.Matcher hourMatcher = hourPattern.matcher(nameWithoutExtension);
            if (hourMatcher.find()) {
                String hourCandidate = hourMatcher.group(1);
                if (isValidHourString(hourCandidate)) {
                    hour = hourCandidate;
                }
            }
        }
        
        return new FileDateTimeInfo(normalizeString(date), normalizeString(hour));
    }
    
    private boolean isValidHourString(String hour) {
        if (hour == null || hour.length() != 2 || !hour.matches("\\d{2}")) {
            return false;
        }
        int hourValue = Integer.parseInt(hour);
        return hourValue >= 0 && hourValue <= 23;
    }
    
    private static class FileDateTimeInfo {
        private final String date;
        private final String hour;
        
        private FileDateTimeInfo(String date, String hour) {
            this.date = date;
            this.hour = hour;
        }
        
        private String getDate() {
            return date;
        }
        
        private String getHour() {
            return hour;
        }
    }
    /**
     * 判断字符串是否像日期
     */
    private boolean isDateLike(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        
        // 判断是否为数字且长度为4-8位
        return str.matches("\\d{4,8}") || str.matches("\\d{4}[\\-/]\\d{2}[\\-/]\\d{2}");
    }
    
    /**
     * 判断字符串是否为纯数字
     */
    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        return str.matches("\\d+");
    }
    
    /**
     * 从文件名中提取日期
     * 支持多种不规范的文件命名格式，使用多种策略进行日期提取
     */
    private String extractDateFromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 移除文件后缀
            String nameWithoutExtension = fileName;
            if (fileName.toLowerCase().endsWith(".nc")) {
                nameWithoutExtension = fileName.substring(0, fileName.length() - 3);
            }
            
            // 策祥1：查找8位数字的日期格式（YYYYMMDD）
            java.util.regex.Pattern datePattern8 = java.util.regex.Pattern.compile("(\\d{8})");
            java.util.regex.Matcher matcher8 = datePattern8.matcher(nameWithoutExtension);
            
            while (matcher8.find()) {
                String dateStr = matcher8.group(1);
                if (isValidDateString(dateStr)) {
                    System.out.println("    日期提取（策祥1-8位）: " + dateStr + " 从 " + fileName);
                    return dateStr;
                }
            }
            
            // 策祥2：查找6位数字的日期格式（YYMMDD），转换为8位
            java.util.regex.Pattern datePattern6 = java.util.regex.Pattern.compile("(\\d{6})");
            java.util.regex.Matcher matcher6 = datePattern6.matcher(nameWithoutExtension);
            
            while (matcher6.find()) {
                String dateStr6 = matcher6.group(1);
                // 尝试转换为8位日期
                String dateStr8 = convertSixDigitToEightDigit(dateStr6);
                if (dateStr8 != null && isValidDateString(dateStr8)) {
                    System.out.println("    日期提取（策祥2-6位转8位）: " + dateStr8 + " 从 " + fileName);
                    return dateStr8;
                }
            }
            
            // 策祥3：查找YYYY-MM-DD或YYYY/MM/DD格式
            java.util.regex.Pattern datePatternSep = java.util.regex.Pattern.compile("(\\d{4})[\\-/](\\d{2})[\\-/](\\d{2})");
            java.util.regex.Matcher matcherSep = datePatternSep.matcher(nameWithoutExtension);
            
            if (matcherSep.find()) {
                String year = matcherSep.group(1);
                String month = matcherSep.group(2);
                String day = matcherSep.group(3);
                String dateStr = year + month + day;
                if (isValidDateString(dateStr)) {
                    System.out.println("    日期提取（策祥3-分隔符）: " + dateStr + " 从 " + fileName);
                    return dateStr;
                }
            }
            
            // 策祥4：查找YYYYMMDD或YYYYMMD或YYYMMDD等不规范格式
            java.util.regex.Pattern flexPattern = java.util.regex.Pattern.compile("(\\d{4})(\\d{1,2})(\\d{1,2})");
            java.util.regex.Matcher flexMatcher = flexPattern.matcher(nameWithoutExtension);
            
            while (flexMatcher.find()) {
                String year = flexMatcher.group(1);
                String month = String.format("%02d", Integer.parseInt(flexMatcher.group(2)));
                String day = String.format("%02d", Integer.parseInt(flexMatcher.group(3)));
                String dateStr = year + month + day;
                if (isValidDateString(dateStr)) {
                    System.out.println("    日期提取（策祥4-灵活匹配）: " + dateStr + " 从 " + fileName);
                    return dateStr;
                }
            }
            
            // 策祥5：从文件路径中寻找日期信息（可能在目录名中）
            // 这里可以在今后扩展，根据实际情况添加更多策略
            
            System.out.println("    警告：无法从文件名提取日期: " + fileName);
            return null;
            
        } catch (Exception e) {
            System.err.println("从文件名提取日期失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 将66位数字日期转换为8位数字日期
     * @param sixDigitDate YYMMDD格式的日期
     * @return YYYYMMDD格式的日期，如果无法转换则返回null
     */
    private String convertSixDigitToEightDigit(String sixDigitDate) {
        if (sixDigitDate == null || sixDigitDate.length() != 6) {
            return null;
        }
        
        try {
            int year2 = Integer.parseInt(sixDigitDate.substring(0, 2));
            String monthDay = sixDigitDate.substring(2);
            
            // 将两位年份转换为四位年份
            // 如果年份大于50，则认为是19XX，否则认为是20XX
            int year4;
            if (year2 > 50) {
                year4 = 1900 + year2;
            } else {
                year4 = 2000 + year2;
            }
            
            return String.valueOf(year4) + monthDay;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 验证日期字符串是否合理
     * @param dateStr YYYYMMDD格式的日期字符串
     * @return 是否合理
     */
    private boolean isValidDateString(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return false;
        }
        
        try {
            int year = Integer.parseInt(dateStr.substring(0, 4));
            int month = Integer.parseInt(dateStr.substring(4, 6));
            int day = Integer.parseInt(dateStr.substring(6, 8));
            
            // 基本验证规则
            if (year < 1900 || year > 2100) {
                return false;
            }
            if (month < 1 || month > 12) {
                return false;
            }
            if (day < 1 || day > 31) {
                return false;
            }
            
            // 简单的月份天数验证
            if (month == 2 && day > 29) {
                return false; // 二月不能超过29天
            }
            if ((month == 4 || month == 6 || month == 9 || month == 11) && day > 30) {
                return false; // 小月不能超过30天
            }
            
            return true;
                   
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 计算相对路径
     */
    private String calculateRelativePath(String absolutePath) {
        if (absolutePath == null) return null;
        
        try {
            Path outputRoot = Paths.get(configManager.getOutputDirectory());
            Path absolutePathObj = Paths.get(absolutePath);
            
            if (absolutePathObj.startsWith(outputRoot)) {
                Path relativePath = outputRoot.relativize(absolutePathObj);
                return "/NC/" + relativePath.toString().replace("\\", "/"); // 统一使用正斜杠
            }
        } catch (Exception e) {
            System.err.println("    计算相对路径失败: " + e.getMessage());
        }
        
        return absolutePath; // 如果计算失败，返回绝对路径
    }
    
    /**
     * 从时间戳中提取timer
     */
    
    /**
     * 计算输出目录路径，保持目录结构
     */
    private String calculateOutputDirectory(Path ncFilePath) {
        // 获取输入和输出根目录
        Path inputRoot = Paths.get(configManager.getInputDirectory());
        Path outputRoot = Paths.get(configManager.getOutputDirectory());
        
        // 计算相对路径
        Path relativePath = inputRoot.relativize(ncFilePath.getParent());
        
        // 创建对应的输出目录结构
        Path outputDir = outputRoot.resolve(relativePath);
        
        return outputDir.toString();
    }
    
    /**
     * 保存图片并保持目录结构
     */
    private String saveImageWithDirectoryStructure(Path ncFilePath) throws IOException {
        // 获取输入和输出根目录
        Path inputRoot = Paths.get(configManager.getInputDirectory());
        Path outputRoot = Paths.get(configManager.getOutputDirectory());
        
        // 计算相对路径
        Path relativePath = inputRoot.relativize(ncFilePath.getParent());
        
        // 创建对应的输出目录结构
        Path outputDir = outputRoot.resolve(relativePath);
        Files.createDirectories(outputDir);
        
        // // 生成输出文件名
        // String imageFileName = ncFilePath.getFileName().toString().replaceAll("\\.nc$", ".png");
        // Path outputFile = outputDir.resolve(imageFileName);
        
        // // 保存图片
        // ImageIO.write(image, "PNG", outputFile.toFile());
        
        // System.out.println("红黑图已保存: " + outputFile);
        // System.out.println("目录结构: " + relativePath + " -> " + imageFileName);
        
        return outputDir.toString();
    }
    
    public static void main(String[] args) {
        NCFileProcessor processor = new NCFileProcessor();
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(processor::stop));
        
        // 启动处理器
        processor.start();
        
        // 保持程序运行
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}


