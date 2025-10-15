package com.geovis.tools.png;

import com.example.NumberUtils;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * @Author: Gengfangdong
 * @Description:
 * @FileName: org.sq.tools.PngUtils
 * @Date: 2022/10/21 14:52
 * @Version: 1.0
 */
@Slf4j
public class PngUtils {

    /**
     * 原始数据
     * 11 12
     * 21 22
     * png
     * 21 22
     * 11 12
     * 写数据到png 同时生成json
     * 默认从左下角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     * bufferedImage 左上角为(0,0)
     *
     * @param pngName png名字
     * @param data    二维数组 从左下角开始写
     * @param width
     * @param height
     */
    public static void writeDataToPngFromLeftBottom(String pngName, double[][] data, int width, int height) {

        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double min = 999999;
        double max = -999999;
        for (int i = 0, count = data.length; i < count; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j] == -999 || data[i][j] == 999999 || data[i][j] == -9999 || data[i][j] > 999999 || data[i][j] == 9999 || data[i][j] == NumberUtils.inputInvalidValue) {
                    continue;
                }
                if (min > data[i][j]) {
                    min = data[i][j];
                }
                if (max < data[i][j]) {
                    max = data[i][j];
                }
            }
        }
        max = Double.parseDouble(NumberUtils.scienceD(max));
        min = Double.parseDouble(NumberUtils.scienceD(min));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (data[j][i] == 999999 || data[j][i] == -999 || data[j][i] == -9999.0 || data[j][i] >= 999999 || Double.isNaN(data[j][i]) ||
                        data[j][i] == NumberUtils.inputInvalidValue) {
                    // 无效值 透明度为0
                    a = 0;
                } else {
                    // 有效值 设置透明度为255
                    a = 255;
                }
                // 透明度|red|green|blue
                int rgb = (a << 24) | (getRgbaValue(min, max, data[j][i]) << 16) | (0 << 8) | 0;
                // 从左下角开始渲染
                bufImg.setRGB(i, height - j - 1, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
//        log.info("父目录:{},{}", file.getParentFile().getAbsoluteFile().getAbsolutePath(), file.getParentFile().getAbsoluteFile().exists());
        if (!file.getParentFile().getAbsoluteFile().exists()) {
            log.info("创建父目录:{}", file.getParentFile().getAbsoluteFile().mkdirs());
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
//            log.info("文件写入成功，路径为:{},大小为:{} KB", pngName, file.length() / 1024);
        } catch (IOException e) {
            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 原始数据
     * 11 12
     * 21 22
     * png
     * 11 12
     * 21 22
     * 写数据到png 同时生成json
     * 默认从左上角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     * bufferedImage 左上角为(0,0)
     *
     * @param pngName png名字
     * @param data    二维数组 从左上角开始写
     * @param width
     * @param height
     */
    public static void writeDataToPngFromLeftTop(String pngName, double[][] data, int width, int height) {

        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double min = 999999;
        double max = -999999;
        for (int i = 0, count = data.length; i < count; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (data[i][j] == -999 || data[i][j] == 999999 || data[i][j] == -9999 || data[i][j] > 999999 || data[i][j] == 9999 || data[i][j] == NumberUtils.inputInvalidValue) {
                    continue;
                }
                if (min > data[i][j]) {
                    min = data[i][j];
                }
                if (max < data[i][j]) {
                    max = data[i][j];
                }
            }
        }
        max = Double.parseDouble(NumberUtils.scienceD(max));
        min = Double.parseDouble(NumberUtils.scienceD(min));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (data[j][i] == 999999 || data[j][i] == -999 || data[j][i] == -9999.0 || data[j][i] >= 999999 || Double.isNaN(data[j][i]) ||
                        data[j][i] == NumberUtils.inputInvalidValue) {
                    // 无效值 透明度为0
                    a = 0;
                } else {
                    // 有效值 设置透明度为255
                    a = 255;
                }
                // 透明度|red|green|blue
                int rgb = (a << 24) | (getRgbaValue(min, max, data[j][i]) << 16) | (0 << 8) | 0;
                // 从左下角开始渲染
                bufImg.setRGB(i, j, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
        log.info("父目录:{},{}", file.getParentFile().getAbsoluteFile().getAbsolutePath(), file.getParentFile().getAbsoluteFile().exists());
        if (!file.getParentFile().getAbsoluteFile().exists()) {
            log.info("创建父目录:{}", file.getParentFile().getAbsoluteFile().mkdirs());
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
            log.info("文件写入成功，路径为:{},大小为:{} bytes", pngName, file.length());
        } catch (IOException e) {
            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 原始数据
     * 11 12
     * 21 22
     * data 11 12 21 22
     * png
     * 21 22
     * 11 12
     * 写数据到png 同时生成json
     * 默认从左下角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     *
     * @param pngName png名字
     * @param data    一维数组 从左上角的数据开始 按照行压缩 就是先写一行再写一行
     * @param width
     * @param height
     */
    public static void writeDataToPngFromLeftBottom(String pngName, double[] data, int width, int height) {

        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double min = 999999;
        double max = -999999;
        for (int i = 0, count = data.length; i < count; i++) {
            if (data[i] == 999999) {
                continue;
            }
            if (data[i] == -999 || data[i] == 999999 || data[i] == -9999 || data[i] > 999999 || data[i] == 9999) {
                continue;
            }
            if (min > data[i]) {
                min = data[i];
            }
            if (max < data[i]) {
                max = data[i];
            }
        }
        max = Double.parseDouble(NumberUtils.scienceD(max));
        min = Double.parseDouble(NumberUtils.scienceD(min));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (data[j * width + i] == 999999 || data[j * width + i] == -999 || data[j * width + i] == -9999.0 || data[j * width + i] >= 999999 || Double.isNaN(data[j * width + i]) ||
                        data[j * width + i] == NumberUtils.inputInvalidValue) {
                    // 有效值 透明度为0
                    a = 0;
                } else {
                    // 无效值 设置透明度为255
                    a = 255;
                }
                // 透明度|red|green|blue
                int rgb = (a << 24) | (getRgbaValue(min, max, data[j * width + i]) << 16) | (0 << 8) | 0;
                // 从左下角开始渲染
                bufImg.setRGB(i, height - j - 1, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
        log.info("父目录:{},{}", file.getParentFile().getAbsoluteFile().getAbsolutePath(), file.getParentFile().getAbsoluteFile().exists());
        if (!file.getParentFile().getAbsoluteFile().exists()) {
            log.info("创建父目录:{}", file.getParentFile().getAbsoluteFile().mkdirs());
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
            log.info("文件写入成功，路径为:{}", pngName);
        } catch (IOException e) {

            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 原始数据
     * 11 12
     * 21 22
     * data 11 12 21 22
     * png
     * 11 12
     * 21 22
     * 写数据到png 同时生成json
     * 从左上角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     *
     * @param pngName png名字
     * @param data
     * @param width
     * @param height
     */
    public static void writeDataToPngFromLeftTop(String pngName, double[] data, int width, int height) {
        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double min = 999999;
        double max = -999999;
        for (int i = 0, count = data.length; i < count; i++) {
            if (data[i] == 999999) {
                continue;
            }
            if (data[i] == -999 || data[i] == 999999 || data[i] == -9999 || data[i] > 999999 || data[i] == 9999) {
                continue;
            }
            if (min > data[i]) {
                min = data[i];
            }
            if (max < data[i]) {
                max = data[i];
            }
        }
        max = Double.parseDouble(NumberUtils.scienceD(max));
        min = Double.parseDouble(NumberUtils.scienceD(min));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (data[j * width + i] == 999999 || data[j * width + i] == -999 || data[j * width + i] == -9999.0 || data[j * width + i] >= 999999 || Double.isNaN(data[j * width + i]) ||
                        data[j * width + i] == NumberUtils.inputInvalidValue) {
                    // 有效值 透明度为0
                    a = 0;
                } else {
                    // 无效值 设置透明度为255
                    a = 255;
                }
                // 透明度|red|green|blue
                int rgb = (a << 24) | (getRgbaValue(min, max, data[j * width + i]) << 16) | (0 << 8) | 0;
                // 从左上角开始渲染
                bufImg.setRGB(i, j, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
            log.info("文件写入成功，路径为:{}", pngName);
        } catch (IOException e) {

            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 原始数据
     * 11 12
     * 21 22
     * data 11 12 21 22
     * png
     * 21 22
     * 11 12
     * 写数据到png 同时生成json
     * 默认从左下角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     *
     * @param pngName png名字
     * @param uData   一维数组 从左上角的数据开始 按照行压缩 就是先写一行再写一行 u风
     * @param vData   v风结构如上
     * @param width
     * @param height
     */
    public static void writeUVDataToPngFromLeftBottom(String pngName, double[] uData, double[] vData, int width, int height) {

        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double minU = 999999;
        double maxU = -999999;
        double minV = 999999;
        double maxV = -999999;
        for (int i = 0, count = uData.length; i < count; i++) {
            if (uData[i] == 999999) {
                continue;
            }
            if (uData[i] == -999 || uData[i] == 999999 || uData[i] == -9999 || uData[i] > 999999 || uData[i] == 9999
                    || vData[i] == -999 || vData[i] == 999999 || vData[i] == -9999 || vData[i] > 999999 || vData[i] == 9999) {
                continue;
            }
            if (minU > uData[i]) {
                minU = uData[i];
            }
            if (maxU < uData[i]) {
                maxU = uData[i];
            }
            if (minV > vData[i]) {
                minV = vData[i];
            }
            if (maxV < vData[i]) {
                maxV = vData[i];
            }
        }
        maxU = Double.parseDouble(NumberUtils.scienceD(maxU));
        minU = Double.parseDouble(NumberUtils.scienceD(minU));
        maxV = Double.parseDouble(NumberUtils.scienceD(maxV));
        minV = Double.parseDouble(NumberUtils.scienceD(minV));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (uData[j * width + i] == 999999 || uData[j * width + i] == -999 || uData[j * width + i] == -9999.0 || uData[j * width + i] >= 999999 || Double.isNaN(uData[j * width + i]) ||
                        uData[j * width + i] == NumberUtils.inputInvalidValue ||
                        vData[j * width + i] == 999999 || vData[j * width + i] == -999 || vData[j * width + i] == -9999.0 || vData[j * width + i] >= 999999 || Double.isNaN(vData[j * width + i]) ||
                        vData[j * width + i] == NumberUtils.inputInvalidValue) {
                    // 有效值 透明度为0
                    a = 0;
                } else {
                    // 无效值 设置透明度为255
                    a = 255;
                }
                // 透明度|u red|green|v blue
                int rgb = (a << 24) | (getRgbaValue(minU, maxU, uData[j * width + i]) << 16) | (getRgbaValue(minV, maxV, vData[j * width + i]) << 8) | 0;
                // 从左下角开始渲染
                bufImg.setRGB(i, height - j - 1, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
        log.info("父目录:{},{}", file.getParentFile().getAbsoluteFile().getAbsolutePath(), file.getParentFile().getAbsoluteFile().exists());
        if (!file.getParentFile().getAbsoluteFile().exists()) {
            log.info("创建父目录:{}", file.getParentFile().getAbsoluteFile().mkdirs());
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
            log.info("文件写入成功，路径为:{}", pngName);
        } catch (IOException e) {

            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 原始数据
     * 11 12
     * 21 22
     * data 11 12 21 22
     * png
     * 21 22
     * 11 12
     * 写数据到png 同时生成json
     * 默认从左下角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     *
     * @param pngName png名字
     * @param uData   一维数组 从左上角的数据开始 按照行压缩 就是先写一行再写一行 u风
     * @param vData   v风结构如上
     * @param width
     * @param height
     */
    public static void writeUVDataToPngFromLeftBottom(String pngName, double[][] uData, double[][] vData, int width, int height) {

        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double minU = 999999;
        double maxU = -999999;
        double minV = 999999;
        double maxV = -999999;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (uData[j][i] == 999999) {
                    continue;
                }
                if (uData[j][i] == -999 || uData[j][i] == 999999 || uData[j][i] == -9999 || uData[j][i] > 999999 || uData[j][i] == 9999
                        || vData[j][i] == -999 || vData[j][i] == 999999 || vData[j][i] == -9999 || vData[j][i] > 999999 || vData[j][i] == 9999) {
                    continue;
                }
                if (minU > uData[j][i]) {
                    minU = uData[j][i];
                }
                if (maxU < uData[j][i]) {
                    maxU = uData[j][i];
                }
                if (minV > vData[j][i]) {
                    minV = vData[j][i];
                }
                if (maxV < vData[j][i]) {
                    maxV = vData[j][i];
                }
            }
        }
        maxU = Double.parseDouble(NumberUtils.scienceD(maxU));
        minU = Double.parseDouble(NumberUtils.scienceD(minU));
        maxV = Double.parseDouble(NumberUtils.scienceD(maxV));
        minV = Double.parseDouble(NumberUtils.scienceD(minV));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (uData[j][i] == 999999 || uData[j][i] == -999 || uData[j][i] == -9999.0 || uData[j][i] >= 999999 || Double.isNaN(uData[j][i]) ||
                        uData[j][i] == NumberUtils.inputInvalidValue ||
                        vData[j][i] == 999999 || vData[j][i] == -999 || vData[j][i] == -9999.0 || vData[j][i] >= 999999 || Double.isNaN(vData[j][i]) ||
                        vData[j][i] == NumberUtils.inputInvalidValue) {
                    // 有效值 透明度为0
                    a = 0;
                } else {
                    // 无效值 设置透明度为255
                    a = 255;
                }
                // 透明度|u red|green|v blue
                int rgb = (a << 24) | (getRgbaValue(minU, maxU, uData[j][i]) << 16) | (getRgbaValue(minV, maxV, vData[j][i]) << 8) | 0;
                // 从左下角开始渲染
//                bufImg.setRGB(i, height - j - 1, rgb);
                bufImg.setRGB(i, j, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
        log.info("父目录:{},{}", file.getParentFile().getAbsoluteFile().getAbsolutePath(), file.getParentFile().getAbsoluteFile().exists());
        if (!file.getParentFile().getAbsoluteFile().exists()) {
            log.info("创建父目录:{}", file.getParentFile().getAbsoluteFile().mkdirs());
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
            log.info("文件写入成功，路径为:{}", pngName);
        } catch (IOException e) {

            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 原始数据
     * 11 12
     * 21 22
     * data 11 12 21 22
     * png
     * 11 12
     * 21 22
     * 写数据到png 同时生成json
     * 从左上角开始渲染
     * 图是从左上角为坐标原点 x轴为经度 y轴为维度
     *
     * @param pngName png名字
     * @param uData
     * @param vData
     * @param width
     * @param height
     */
    public static void writeUVDataToPngFromRightTop(String pngName, double[] uData, double[] vData, int width, int height) {
        BufferedImage bufImg = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = bufImg.createGraphics();
        java.awt.geom.Rectangle2D.Double r = new java.awt.geom.Rectangle2D.Double(0, 0, width, height);
        g2d.fill(r);
        double minU = 999999;
        double maxU = -999999;
        double minV = 999999;
        double maxV = -999999;
        for (int i = 0, count = uData.length; i < count; i++) {
            if (uData[i] == 999999) {
                continue;
            }
            if (uData[i] == -999 || uData[i] == 999999 || uData[i] == -9999 || uData[i] > 999999 || uData[i] == 9999
                    || vData[i] == -999 || vData[i] == 999999 || vData[i] == -9999 || vData[i] > 999999 || vData[i] == 9999) {
                continue;
            }
            if (minU > uData[i]) {
                minU = uData[i];
            }
            if (maxU < uData[i]) {
                maxU = uData[i];
            }
            if (minV > vData[i]) {
                minV = vData[i];
            }
            if (maxV < vData[i]) {
                maxV = vData[i];
            }
        }
        maxU = Double.parseDouble(NumberUtils.scienceD(maxU));
        minU = Double.parseDouble(NumberUtils.scienceD(minU));
        maxV = Double.parseDouble(NumberUtils.scienceD(maxV));
        minV = Double.parseDouble(NumberUtils.scienceD(minV));
        // 透明度
        int a = 255;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (uData[j * width + i] == 999999 || uData[j * width + i] == -999 || uData[j * width + i] == -9999.0 || uData[j * width + i] >= 999999 || Double.isNaN(uData[j * width + i]) ||
                        uData[j * width + i] == NumberUtils.inputInvalidValue ||
                        vData[j * width + i] == 999999 || vData[j * width + i] == -999 || vData[j * width + i] == -9999.0 || vData[j * width + i] >= 999999 || Double.isNaN(vData[j * width + i]) ||
                        vData[j * width + i] == NumberUtils.inputInvalidValue) {
                    // 有效值 透明度为0
                    a = 0;
                } else {
                    // 无效值 设置透明度为255
                    a = 255;
                }
                // 透明度|red|green|blue
                int rgb = (a << 24) | (getRgbaValue(minU, maxU, uData[j * width + i]) << 16) | (getRgbaValue(minV, maxV, vData[j * width + i]) << 8) | 0;
                // 从左上角开始渲染
                bufImg.setRGB(i, j, rgb);
            }
        }

        g2d.dispose();
        File file = new File(pngName.replace(".nc", ""));
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            file.createNewFile();
            ImageIO.write(bufImg, "png", file);
            log.info("文件写入成功，路径为:{}", pngName);
        } catch (IOException e) {

            log.error("文件写入失败，路径为:{}===>{}", pngName, e);
        }
    }

    /**
     * 根据最大最小和 当前值 获取r的值 就是按照比例划分
     *
     * @param min   最小
     * @param max   最大
     * @param value 当前值
     * @return
     */
    private static int getRgbaValue(double min, double max, double value) {
        if (value == 999999 || Double.isNaN(value) || value == -999 || value == -9999 || value > 999999 || Double.isNaN(value) || value == NumberUtils.inputInvalidValue) {
            return 0;
        }
        return (int) (Math.abs((value - min) / (max - min)) * 255);
    }
}