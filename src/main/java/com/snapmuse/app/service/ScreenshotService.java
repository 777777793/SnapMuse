package com.snapmuse.app.service;

import com.snapmuse.app.model.AppConfig;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class ScreenshotService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final ConfigService configService;

    public ScreenshotService(ConfigService configService) {
        this.configService = configService;
    }

    public Path capture(Point firstPoint, Point secondPoint) throws IOException, AWTException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("当前环境不支持桌面截图");
        }

        int left = Math.min(firstPoint.x, secondPoint.x);
        int top = Math.min(firstPoint.y, secondPoint.y);
        int width = Math.max(1, Math.abs(firstPoint.x - secondPoint.x));
        int height = Math.max(1, Math.abs(firstPoint.y - secondPoint.y));

        Robot robot = new Robot();
        BufferedImage image = robot.createScreenCapture(new Rectangle(left, top, width, height));

        AppConfig config = configService.getConfig();
        Path outputDirectory = Path.of(config.getScreenshotDirectory());
        Files.createDirectories(outputDirectory);
        Path outputFile = outputDirectory.resolve("snap-" + FORMATTER.format(LocalDateTime.now()) + ".png");
        ImageIO.write(image, "png", outputFile.toFile());
        return outputFile;
    }
}
