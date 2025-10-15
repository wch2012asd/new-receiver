package com.geovis.tools.json;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * @Author: gengfangdong
 * @Description
 * @FileName: JsonUtils
 * @Date: 2022/11/12 14:06
 * @Version: 1.0
 */
public class JsonUtils {
    /**
     * 写json 到文件
     *
     * @param min
     * @param max
     * @param width
     * @param height
     * @param lonMin
     * @param latMin
     * @param lonMax
     * @param latMax
     * @param lonStep
     * @param latStep
     * @param unit
     * @param path
     */
    public static void writeJsonToFile(String min, String max, String width, String height, String lonMin, String latMin, String lonMax, String latMax, String lonStep, String latStep, String unit, String path) {
        String json = "{\"min\": " + min + ", \"max\": " + max + ", \"width\": " + width + ", \"height\": " + height + ", \"lonmin\": " + lonMin + ", \"latmin\": "
                + latMin + ", \"lonmax\": " + lonMax + ", \"latmax\": " + latMax + ", \"lonstep\": " + lonStep + ", \"latstep\": " + latStep + ", \"unit\": \"" + unit + "\"}";
        writeStringToFile(json, path.replace(".png", ".json").replace(".nc", ""));
    }

    /**
     * 写json 到文件
     *
     * @param maxu
     * @param maxv
     * @param minu
     * @param minv
     * @param width
     * @param height
     * @param lonMin
     * @param latMin
     * @param lonMax
     * @param latMax
     * @param lonStep
     * @param latStep
     * @param unit
     * @param path
     */
    public static void writeJsonToFile(String minu, String minv, String maxu, String maxv, String width, String height, String lonMin, String latMin, String lonMax, String latMax, String lonStep, String latStep, String unit, String path) {
        String json = "{\"minu\": " + minu + ", \"maxu\": " + maxu + ", \"minv\": " + minv + ", \"maxv\": " + maxv + ", \"width\": " + width + ", \"height\": "
                + height + ", \"lonmin\": " + lonMin + ", \"latmin\": " + latMin + ", \"lonmax\": " + lonMax + ", \"latmax\": " + latMax + ", \"lonstep\": " + lonStep + ", \"latstep\": "
                + latStep + ", \"unit\": \"" + unit + "\"}";
        writeStringToFile(json, path.replace(".png", ".json").replace(".nc", ""));
    }

    /**
     * 写字符串到文件
     *
     * @param content 内容
     * @param filePath 文件路径
     */
    private static void writeStringToFile(String content, String filePath) {

        Path path = Paths.get(filePath);
        
        try {
            // 创建父目录（如果不存在）
            Files.createDirectories(path.getParent());
            
            // 写入文件内容（会自动创建文件）
            //Files.writeString(path, content);
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            System.out.println("文件写入成功: " + filePath);
            
        } catch (IOException e) {
            System.err.println("文件操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * json文件
     *
     * @param jsonFile
     * @return
     */
    public static String readJsonFile(File jsonFile) {
        if (!jsonFile.exists()) {
            return null;
        }
        StringBuilder jsonStr = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(jsonFile.toPath());
            for (String line : lines) {
                jsonStr.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonStr.toString();
    }
}
