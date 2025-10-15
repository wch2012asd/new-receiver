package com.example;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author: Gengfangdong
 * @Description:
 * @FileName: NcToPngUtils
 * @Date: 2023/6/14 18:30
 * @Version: 1.0
 */
@Slf4j
public class NcToPngUtils {

    static final long SECONDS_FROM_1900_TO_1970 = 2208988800L;
    public static int minRank = 2;
    private static final String[] WIND_U_PREFIXES = new String[]{
            "uu", "u10", "UU", "u_", "U_", "u", "U", "U_component_of_wind_isobaric"
    };
    private static final String[] WIND_V_PREFIXES = new String[]{
            "vv", "v10", "VV", "v_", "V_", "v", "V", "V_component_of_wind_isobaric"
    };
    private static final String[] WIND_UV_PREFIXES = new String[]{
            "uv", "uv10", "UV", "uv_", "UV_", "uv", "UV", "UV_component_of_wind_isobaric"
    };
    public static void main(String[] args) {
        List<NcBeanModel> ncBeanModelList = ncToPng("E:\\mnt\\data\\HQOH\\new1.nc", "E:\\mnt\\data\\HQOH\\1");
        for (NcBeanModel ncBeanModel : ncBeanModelList) {
            System.out.println(ncBeanModel);
        }
    }

    /**
     * nc 转 png
     * 所有的要素
     *
     * @param filePath
     * @param pngPath
     */
    public static List<NcBeanModel> ncToPng(String filePath, String pngPath) {
        List<NcBeanModel> ncBeanModelList = new ArrayList<>();
        try (NetcdfFile netcdfFile = NetcdfFile.open(filePath)) {
            // 获取要素
            List<Variable> variables = netcdfFile.getVariables();
            Map<String, Variable> variableMap = NcReader.variablesTranToShortNameVariableMap(variables);
            for (Map.Entry<String, Variable> variableEntry : variableMap.entrySet()) {
                // 简单过滤一下
                Variable variable = variableEntry.getValue();
                if (variable.getRank() >= minRank && !StringUtils.equalsAnyIgnoreCase(variable.getShortName(), "time")) {
                    try {
                        ncBeanModelList.addAll(variableToPng(pngPath, variable, variableMap));
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("要素:{},出图失败!", variable.getShortName());
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return ncBeanModelList;
    }

    /**
     * nc 转 png
     *
     * @param filePath
     * @param pngPath
     */
    public static List<NcBeanModel> ncToPng(String filePath, String pngPath, String variableName) {
        List<NcBeanModel> ncBeanModelList = new ArrayList<>();
        try (NetcdfFile netcdfFile = NetcdfFile.open(filePath)) {
            // 获取要素
            List<Variable> variables = netcdfFile.getVariables();
            Map<String, Variable> variableMap = NcReader.variablesTranToShortNameVariableMap(variables);
            // 获取要读取的要素
            Variable variable = variableMap.get(variableName);
            if (variable == null) {
                throw new RuntimeException("要素:" + variableName + "不存在");
            }
            ncBeanModelList.addAll(variableToPng(pngPath, variable, variableMap));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ncBeanModelList;
    }

    /**
     * variable 转 png
     *
     * @param pngPath
     * @param variable
     * @param variableMap
     */
    private static List<NcBeanModel> variableToPng(String pngPath, Variable variable, Map<String, Variable> variableMap) {
        List<NcBeanModel> ncBeanModelList = new ArrayList<>();
        String variableName = variable.getShortName();
        int rank = variable.getRank();
        // 要素维度小于2的话 不是 geo2D 直接剔除
        if (rank < minRank) {
            log.error("要素:{} 维度为:{},最少为2维!", variableName, rank);
            return Collections.emptyList();
        }
        // 读取经纬度数据
        if (rank == minRank) {
            // 如果为 2维 的 直接转png
            ncBeanModelList.addAll(variableToPng(variable, null, null, variableMap, pngPath, variableName, null, null, pngPath));
        } else {
            deepBuildDimensionData(variableMap, variable.getDimensions(), 0, new ArrayList<>(), variableName, pngPath, variable, ncBeanModelList, pngPath);
        }
        return ncBeanModelList;
    }

    /**
     * 获取当前层的所有节点数据
     *
     * @param variableMap     要素map
     * @param dimensionList   维度名称
     * @param layerIndex      当前维度层 layer
     * @param preOrg          读取起始位置
     * @param prefix          路径前缀
     * @param dataVariable    数据要素
     * @param filePath        文件路径
     * @param ncBeanModelList 存储bean
     * @return
     */
    private static void deepBuildDimensionData(Map<String, Variable> variableMap, List<Dimension> dimensionList, Integer layerIndex, List<Integer> preOrg, String prefix, String filePath, Variable dataVariable, List<NcBeanModel> ncBeanModelList, String ncFilePath) {
        if (layerIndex >= dimensionList.size()) {
            // 代表超过了 维度 直接返回
            return;
        }
        // 获取维度名称
        String shortName = dimensionList.get(layerIndex).getShortName();
        Variable variable = variableMap.get(shortName);
        double[] rasterData = null;
        if (variable != null) {
            // 如果要素不为空 解析数据
            rasterData = readDimensionData(variable, false);
        } else {
            if (StringUtils.equalsAny(shortName, "pressure")) {
                rasterData = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
            } else if (StringUtils.equalsAny(shortName, "single_lev")) {
                rasterData = new double[]{0};
            }
        }
        if (rasterData == null) {
            throw new RuntimeException(MessageFormatter.format("维度:{}不存在!", shortName).getMessage());
        }
        try {
            // 每一条数据
            for (int dimensionIndex = 0; dimensionIndex < rasterData.length; dimensionIndex++) {
                double dimensionItem = rasterData[dimensionIndex];
                // 复制list 下一层节点 读取数据的时候 前面所有层的前缀
                List<Integer> copyPreOrg = new CopyOnWriteArrayList<>();
                List<Integer> copyPreSha = new CopyOnWriteArrayList<>();
                for (Integer org : preOrg) {
                    copyPreOrg.add(org.intValue());
                    copyPreSha.add(1);
                }
                copyPreOrg.add(dimensionIndex);
                copyPreSha.add(1);
                // 当前节点的维度前缀
                String prefixNew = prefix + "_";
                Long time = null;
                if (StringUtils.equalsAnyIgnoreCase(shortName, "time")
                        || StringUtils.equalsAnyIgnoreCase(shortName, "valid_time")) {
                    time = new Double(dimensionItem).longValue();
                    log.info("时间原始值:{}", time);
                    
                    // 获取时间单位属性
                    String timeUnits = variable.findAttribute("units").toString();
                    log.info("时间单位: {}", timeUnits);
                    
                    // 解析时间单位和转换时间戳
                    Long convertedTime = convertTimeToTimestamp(time, timeUnits);
                    if (convertedTime != null) {
                        time = convertedTime;
                        log.info("转换后时间戳: {}", time);
                    }
                    
                    // 格式化时间为日期字符串
                    String formattedDate = formatTimeForFileName(time, timeUnits);
                    log.info("格式化日期: {}", formattedDate);
                    
                    prefixNew += formattedDate;
                } else {
                    prefixNew += String.valueOf(dimensionItem);
                }
                // 下一个维度的索引
                Integer next = layerIndex + 1;
                // 判断当层节点 这里多减1 是因为 从 0开始的
                if (layerIndex == dimensionList.size() - 3) {
                    try {
                        String level = String.valueOf(dimensionItem);
                        if (StringUtils.equals(shortName, "time")) {
                            level = null;
                        }
                        ncBeanModelList.addAll(variableToPng(dataVariable, copyPreOrg, copyPreSha, variableMap, filePath, prefixNew, level, time, ncFilePath));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    deepBuildDimensionData(variableMap, dimensionList, next, copyPreOrg, prefixNew, filePath, dataVariable, ncBeanModelList, ncFilePath);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * @param variable    要解析的要素
     * @param org         起始数据 int数组
     * @param sha         读取要素大小 int 数组
     * @param variableMap 要素map 用来读取纬度和经度
     * @param pngPath     要生成的png 路径
     * @param namePrefix  要生成的png 前缀 用于递归 初始为 文件名
     * @param level       层次
     * @return
     */
    private static List<NcBeanModel> variableToPng(Variable variable, List<Integer> org, List<Integer> sha, Map<String, Variable> variableMap, String pngPath, String namePrefix, String level, Long time, String ncFilePath) {
        String variableName = variable.getShortName();
        // 获取面的数据
        NcDataModel ncDataModel = getFaceData(variable, org, sha);
        if (ncDataModel == null) {
            throw new RuntimeException("要素:" + variableName + "读取失败!");
        }

        // 代码补丁----start
        // 温度类
//        if (StringUtils.equalsAnyIgnoreCase(variableName, "Temperature_isobaric")) {
//            ncDataModel.setAddOffset(-273.15);
//        }


        // 获取经纬度数据
        String latName = ncDataModel.getLatName();
        Variable latVariable = variableMap.get(latName);
        if (latVariable == null) {
            throw new RuntimeException(MessageFormatter.format("纬度:{}不存在!", latName).getMessage());
        }
        String lonName = ncDataModel.getLonName();
        Variable lonVariable = variableMap.get(lonName);
        if (lonVariable == null) {
            throw new RuntimeException(MessageFormatter.format("经度:{}不存在!", lonName).getMessage());
        }
        // 直接读取数据
        double[] lat = readDimensionData(latVariable, false);
        double[] lon = readDimensionData(lonVariable, true);

        // 判断图片渲染方式 ---- start
        // 默认从左下角开始图片渲染
        boolean fromBottom = true;
        boolean fromLeft = true;
        int latLength = lat.length;
        int lonLength = lon.length;
        // 维度从大到小，修改为从上向下开始渲染
        if (latLength > 2 && lat[0] > lat[1]) {
            fromBottom = false;
        }
        // 经度从大到小，修改为从右向左开始渲染
        if (lonLength > 2 && lon[0] > lon[1]) {
            fromLeft = false;
        }
        // 判断图片渲染方式 ---- end

        // 生产要素(包含U分量V分量)
        String toPngPath;
        List<NcBeanModel> ncBeanModelList = new ArrayList<>();
        String timeStr = "_" + new Date().getTime();
        
        // level默认值处理：如果level为null或空，设置默认值为"1000"
        String finalLevel = (level == null || level.trim().isEmpty()) ? "1000" : level;
        
        // 智能时间处理：优先使用文件名中的日期，再结合NC文件中的时间信息
        Long finalTime = intelligentTimeProcessing(time, ncFilePath);
        
        {
            toPngPath = pngPath + File.separator + variableName + File.separator + namePrefix + timeStr + ".png";
            // log.info("开始输出要素:{},红黑图:{}", variableName, toPngPath);
            toPngPath = toPngPath.replace("/", File.separator).replace("\\", File.separator);
            ncDataModel.toPng(toPngPath, lat, lon, fromLeft, fromBottom);
            ncBeanModelList.add(new NcBeanModel().setPngPath(toPngPath).setVariableName(variableName).setLevel(finalLevel).setTime(finalTime));
        }
        // 判断是否为风
//        String[] uNamePrefixArray = new String[]{"uu", "UU", "u_", "U_", "u", "U","U_component_of_wind_isobaric",};
//        String[] vNamePrefixArray = new String[]{"vv", "VV", "v_", "V_", "v", "V","V_component_of_wind_isobaric",};
//        String[] uvNamePrefixArray = new String[]{"uv", "UV", "uv_", "UV_", "uv", "UV","UV_component_of_wind_isobaric",};
//        String uPrefix, vPrefix, uvPrefix;
//        for (int i = 0; i < uNamePrefixArray.length; i++) {
//            uPrefix = uNamePrefixArray[i];
//            vPrefix = vNamePrefixArray[i];
//            uvPrefix = uvNamePrefixArray[i];
//            if (StringUtils.startsWith(variableName, uPrefix)) {
//                log.info("输出风的红黑图!");
//                String vVariableName = variableName.replaceFirst(uPrefix, vPrefix);
//                // 读取 v 风 的要素
//                Variable vVariable = variableMap.get(vVariableName);
//                // 判断是否为UV要素
//                if (vVariable == null) {
//                    continue;
//                }
//                NcDataModel vNcDataModel = getFaceData(vVariable, org, sha);
//                ncDataModel.setVDataArray(vNcDataModel.getDataArray())
//                        .setUDataArray(ncDataModel.getDataArray());
//                String uvVariableName = variableName.replaceFirst(uPrefix, uvPrefix);
//                toPngPath = pngPath + File.separator + uvVariableName + File.separator + namePrefix.replaceAll(variableName, uvVariableName) + timeStr + ".png";
//                ncDataModel.toWindPng(toPngPath, lat, lon);
//                ncBeanModelList.add(new NcBeanModel().setPngPath(toPngPath).setVariableName(uvVariableName).setLevel(finalLevel).setTime(finalTime));
//                break;
//            }
//        }
        // 设置为null 方便gc
        ncDataModel = null;
        return ncBeanModelList;
    }

    private static boolean startsWithAnyPrefix(String value, String[] prefixes) {
        if (value == null || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (StringUtils.startsWith(value, prefix)) {
                return true;
            }
        }
        return false;
    }

    @Data
    public static class Context {
        Variable variable;
        List<Integer> org;
        List<Integer> sha;
        Map<String, Variable> variableMap;
        String pngPath;
        String namePrefix;
        String level;
        Boolean isWind;
        Variable uVariable;
        Variable vVariable;

    }

    /**
     * 获取面数据
     *
     * @param variable 要素实体
     * @param org      起始
     * @param sha      尺寸
     * @return
     */
    private static NcDataModel getFaceData(Variable variable, List<Integer> org, List<Integer> sha) {
        // 如果都为空 代表直接读取数据
        if (org == null && sha == null) {
            return NcReader.readNcDataTrue(variable, null, null);
        }
        if (org == null) {
            org = new ArrayList<>();
        }
        if (sha == null) {
            sha = new ArrayList<>();
        }
        List<Dimension> dimensionList = variable.getDimensions();
        // 获取最后两个维度的rank
        Dimension dimensionLast = dimensionList.get(dimensionList.size() - 1);
        Dimension dimensionPre = dimensionList.get(dimensionList.size() - 2);

        // step 1、增加 为 维度或者经度的起始和偏移
        Integer[] orgInt = org.toArray(new Integer[org.size() + 2]);
        Integer[] shaInt = sha.toArray(new Integer[sha.size() + 2]);
        orgInt[orgInt.length - 2] = 0;
        orgInt[orgInt.length - 1] = 0;
        shaInt[shaInt.length - 2] = dimensionPre.getLength();
        shaInt[shaInt.length - 1] = dimensionLast.getLength();

        // step 2、读取数据
        return NcReader.readNcDataTrue(variable, NumberUtils.convert2IntArray(orgInt), NumberUtils.convert2IntArray(shaInt));
    }

    /**
     * 读取维度的数据
     *
     * @param variable
     * @return
     */
    public static double[] readDimensionData(Variable variable, Boolean row) {
        NcDataModel faceData = getFaceData(variable, null, null);
        if (faceData.getRank() == 1) {
            return faceData.convertDoubleArray();
        } else if (faceData.getRank() == 2 && row) {
            return readTwoArrayRowDataToArray(NumberUtils.transformToDoubleTwoRank(faceData.getDataArray().copyToNDJavaArray()));
        } else if (faceData.getRank() == 3 && row) {
            return readTwoArrayRowDataToArray(NumberUtils.transformToDoubleTwoRank(faceData.getDataArray().reduce().copyToNDJavaArray()));
        }
        if (faceData.getRank() == 3) {
            return readTwoArrayColDataToArray(NumberUtils.transformToDoubleTwoRank(faceData.getDataArray().reduce().copyToNDJavaArray()));
        } else {
            return readTwoArrayColDataToArray(NumberUtils.transformToDoubleTwoRank(faceData.getDataArray().copyToNDJavaArray()));

        }
    }

    /**
     * @param twoArray
     * @return
     */
    private static double[] readTwoArrayRowDataToArray(double[][] twoArray) {
        // 取第一行的数据
        double[] oneArray = new double[twoArray[0].length];
        for (int i = 0; i < twoArray[0].length; i++) {
            oneArray[i] = twoArray[0][i];
        }
        return oneArray;
    }

    /**
     * @param twoArray
     * @return
     */
    private static double[] readTwoArrayColDataToArray(double[][] twoArray) {
        // 取第一行的数据
        double[] oneArray = new double[twoArray.length];
        for (int i = 0; i < twoArray.length; i++) {
            oneArray[i] = twoArray[i][0];
        }
        return oneArray;
    }
    
    /**
     * 将NC文件中的时间值转换为时间戳
     * @param timeValue NC文件中的时间值
     * @param timeUnits 时间单位属性，例如"hours since 1900-01-01 00:00:00.0"
     * @return 转换后的时间戳（毫秒）
     */
    private static Long convertTimeToTimestamp(Long timeValue, String timeUnits) {
        if (timeValue == null || timeUnits == null || timeUnits.trim().isEmpty()) {
            return timeValue;
        }
        
        try {
            // 解析时间单位格式: "hours since 1900-01-01 00:00:00.0"
            // 或 "days since 1900-01-01"
            timeUnits = timeUnits.toLowerCase().trim();
            
            if (!timeUnits.contains("since")) {
                log.warn("无法解析时间单位: {}, 使用原始值", timeUnits);
                return timeValue;
            }
            
            String[] parts = timeUnits.split("since");
            if (parts.length != 2) {
                log.warn("时间单位格式不正确: {}, 使用原始值", timeUnits);
                return timeValue;
            }
            
            String unit = parts[0].trim();
            String baseTimeStr = parts[1].trim();
            
            // 解析基准时间
            long baseTimestamp = parseBaseTime(baseTimeStr);
            
            // 根据单位转换时间值
            long offsetMillis = 0;
            if (unit.startsWith("hour")) {
                offsetMillis = timeValue * 3600 * 1000; // 小时转毫秒
            } else if (unit.startsWith("day")) {
                offsetMillis = timeValue * 24 * 3600 * 1000; // 天转毫秒
            } else if (unit.startsWith("minute")) {
                offsetMillis = timeValue * 60 * 1000; // 分钟转毫秒
            } else if (unit.startsWith("second")) {
                offsetMillis = timeValue * 1000; // 秒转毫秒
            } else {
                log.warn("未知的时间单位: {}, 使用原始值", unit);
                return timeValue;
            }
            
            return baseTimestamp + offsetMillis;
            
        } catch (Exception e) {
            log.error("转换时间失败: {}", e.getMessage());
            return timeValue;
        }
    }
    
    /**
     * 解析基准时间字符串
     * @param baseTimeStr 基准时间字符串，例如"1900-01-01 00:00:00.0"
     * @return 时间戳（毫秒）
     */
    private static long parseBaseTime(String baseTimeStr) {
        try {
            // 清理和规范化时间字符串
            baseTimeStr = baseTimeStr.replace(".0", "").trim();
            
            // 如果只有日期，添加默认时间
            if (!baseTimeStr.contains(" ")) {
                baseTimeStr += " 00:00:00";
            }
            
            // 使用Java时间API解析
            DateTimeFormatter formatter;
            if (baseTimeStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            } else if (baseTimeStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                baseTimeStr += " 00:00:00";
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            } else {
                log.warn("无法解析基准时间格式: {}, 使用默认1900-01-01", baseTimeStr);
                return -2208988800000L; // 1900-01-01 00:00:00 UTC
            }
            
            // 解析时间并转换为时间戳
            java.time.LocalDateTime localDateTime = java.time.LocalDateTime.parse(baseTimeStr, formatter);
            Instant instant = localDateTime.atZone(ZoneId.of("UTC")).toInstant();
            return instant.toEpochMilli();
            
        } catch (Exception e) {
            log.error("解析基准时间失败: {}, 使用默认1900-01-01", e.getMessage());
            return -2208988800000L; // 1900-01-01 00:00:00 UTC
        }
    }
    
    /**
     * 将时间戳格式化为文件名中使用的日期字符串
     * @param timestamp 时间戳（毫秒）
     * @param timeUnits 原始时间单位
     * @return 格式化后的日期字符串，例如"20140101_00"
     */
    private static String formatTimeForFileName(Long timestamp, String timeUnits) {
        if (timestamp == null) {
            return "unknown_time";
        }
        
        try {
            Instant instant = Instant.ofEpochMilli(timestamp);
            DateTimeFormatter formatter = DateTimeFormatter
                    .ofPattern("yyyyMMdd_HH")
                    .withZone(ZoneId.of("UTC"));
            return formatter.format(instant);
        } catch (Exception e) {
            log.error("格式化时间失败: {}, 使用默认格式", e.getMessage());
            return "unknown_time";
        }
    }
    
    /**
     * 智能时间处理：优先使用文件名中的日期，再结合NC文件中的时间信息
     * @param ncTime NC文件中的时间值（可能是时间戳或相对时间）
     * @param ncFilePath NC文件路径
     * @return 智能处理后的时间戳（毫秒）
     */
    private static Long intelligentTimeProcessing(Long ncTime, String ncFilePath) {
        try {
            // 1. 从文件名中提取日期
            String dateFromFileName = extractDateFromFilePath(ncFilePath);
            
            if (dateFromFileName != null) {
                // 文件名中有日期，优先使用
                Long fileNameTimestamp = parseDateToTimestamp(dateFromFileName);
                
                if (fileNameTimestamp != null) {
                    // 2. 尝试从 NC 时间中提取小时信息
                    int hour = extractHourFromNCTime(ncTime);
                    
                    // 合并日期和小时
                    return fileNameTimestamp + (hour * 3600 * 1000L);
                }
            }
            
            // 3. 如果文件名中没有日期或解析失败，使用NC文件中的时间
            if (ncTime != null) {
                // 检查是否已经是时间戳格式（大于1970年的秒数）
                if (ncTime > 31536000L) { // 1970年的秒数
                    // 已经是时间戳，直接返回（转换为毫秒）
                    return ncTime < 1e12 ? ncTime * 1000 : ncTime;
                } else {
                    // 可能是相对时间，使用默认基准时间转换
                    return convertRelativeTimeToTimestamp(ncTime);
                }
            }
            
            // 4. 都失败了，返回当前时间
            log.warn("无法获取有效的时间信息，使用当前时间");
            return System.currentTimeMillis();
            
        } catch (Exception e) {
            log.error("智能时间处理失败: {}", e.getMessage());
            return ncTime != null ? ncTime : System.currentTimeMillis();
        }
    }
    
    /**
     * 从文件路径中提取日期
     * @param filePath 文件路径
     * @return YYYYMMDD 格式的日期字符串
     */
    private static String extractDateFromFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 获取文件名
            String fileName = filePath;
            int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
            if (lastSlash >= 0) {
                fileName = filePath.substring(lastSlash + 1);
            }
            
            // 查找8位数字的日期格式（YYYYMMDD）
            java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("(\\d{8})");
            java.util.regex.Matcher matcher = datePattern.matcher(fileName);
            
            while (matcher.find()) {
                String dateStr = matcher.group(1);
                // 验证是否是合理的日期
                if (isValidDate(dateStr)) {
                    return dateStr;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("从文件路径提取日期失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 验证日期字符串是否合理
     * @param dateStr YYYYMMDD格式的日期字符串
     * @return 是否合理
     */
    private static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return false;
        }
        
        try {
            int year = Integer.parseInt(dateStr.substring(0, 4));
            int month = Integer.parseInt(dateStr.substring(4, 6));
            int day = Integer.parseInt(dateStr.substring(6, 8));
            
            // 简单验证
            return year >= 1900 && year <= 2100 && 
                   month >= 1 && month <= 12 && 
                   day >= 1 && day <= 31;
                   
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 将YYYYMMDD格式的日期转换为时间戳
     * @param dateStr YYYYMMDD格式的日期字符串
     * @return 时间戳（毫秒）
     */
    private static Long parseDateToTimestamp(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return null;
        }
        
        try {
            String formattedDate = dateStr.substring(0, 4) + "-" + 
                                 dateStr.substring(4, 6) + "-" + 
                                 dateStr.substring(6, 8) + " 00:00:00";
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            java.time.LocalDateTime localDateTime = java.time.LocalDateTime.parse(formattedDate, formatter);
            Instant instant = localDateTime.atZone(ZoneId.of("UTC")).toInstant();
            return instant.toEpochMilli();
            
        } catch (Exception e) {
            log.error("解析日期失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 NC 时间值中提取小时信息
     * @param ncTime NC文件中的时间值
     * @return 小时值（0-23）
     */
    private static int extractHourFromNCTime(Long ncTime) {
        if (ncTime == null) {
            return 0;
        }
        
        try {
            // 尝试不同的方法提取小时
            
            // 方法1：如果是小时数（小于8760）
            if (ncTime <= 8760) { // 一年最多8760小时
                return (int) (ncTime % 24);
            }
            
            // 方法2：如果是秒数
            if (ncTime < 1e10) {
                return (int) ((ncTime / 3600) % 24);
            }
            
            // 方法3：如果是毫秒数
            if (ncTime > 1e10) {
                return (int) ((ncTime / 3600000) % 24);
            }
            
            // 默认返回0点
            return 0;
            
        } catch (Exception e) {
            log.error("提取小时信息失败: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 将相对时间转换为绝对时间戳
     * @param relativeTime 相对时间值
     * @return 时间戳（毫秒）
     */
    private static Long convertRelativeTimeToTimestamp(Long relativeTime) {
        if (relativeTime == null) {
            return System.currentTimeMillis();
        }
        
        try {
            // 使用常见的基准时间：1900-01-01 00:00:00 UTC
            long baseTimestamp = -2208988800000L; // 1900-01-01 00:00:00 UTC
            
            // 假设是小时数
            long offsetMillis = relativeTime * 3600 * 1000;
            
            return baseTimestamp + offsetMillis;
            
        } catch (Exception e) {
            log.error("转换相对时间失败: {}", e.getMessage());
            return System.currentTimeMillis();
        }
    }
}
