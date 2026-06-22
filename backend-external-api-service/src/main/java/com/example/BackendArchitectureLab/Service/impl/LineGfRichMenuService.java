package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Service.ILineGfRichMenuService;
import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.richmenu.RichMenu;
import com.linecorp.bot.model.richmenu.RichMenuArea;
import com.linecorp.bot.model.richmenu.RichMenuBounds;
import com.linecorp.bot.model.richmenu.RichMenuIdResponse;
import com.linecorp.bot.model.richmenu.RichMenuSize;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class LineGfRichMenuService implements ILineGfRichMenuService {

    private static final Logger log = LoggerFactory.getLogger(LineGfRichMenuService.class);

    @Autowired
    @Qualifier("gfLineMessagingClient")
    private LineMessagingClient messagingClient;

    @Autowired
    @Qualifier("gfLineBlobClient")
    private LineBlobClient blobClient;

    @PostConstruct
    public void init() {
        if (messagingClient == null) {
            log.warn("GF LINE messaging client not configured, skipping RichMenu registration");
            return;
        }
        try {
            registerRichMenu();
            log.info("GF RichMenu registered successfully");
        } catch (Exception e) {
            log.error("Failed to register GF RichMenu", e);
        }
    }

    @Override
    public void registerRichMenu() {
        try {
            messagingClient.getDefaultRichMenuId().thenAccept(id -> {
                if (id != null && id.getRichMenuId() != null) {
                    messagingClient.deleteRichMenu(id.getRichMenuId()).join();
                }
            }).exceptionally(e -> {
                return null;
            }).join();

            RichMenu richMenu = RichMenu.builder()
                    .size(new RichMenuSize(2500, 1686))
                    .selected(true)
                    .name("女友模式")
                    .chatBarText("女友模式")
                    .areas(List.of(
                            new RichMenuArea(new RichMenuBounds(0, 0, 833, 843), new MessageAction("啟用女友", "#啟用女友")),
                            new RichMenuArea(new RichMenuBounds(833, 0, 834, 843), new MessageAction("提示詞", "#提示詞 ")),
                            new RichMenuArea(new RichMenuBounds(1667, 0, 833, 843), new MessageAction("啟用語音", "#啟用語音")),
                            new RichMenuArea(new RichMenuBounds(0, 843, 833, 843), new MessageAction("關閉女友", "#關閉女友")),
                            new RichMenuArea(new RichMenuBounds(833, 843, 834, 843), new MessageAction("狀態", "#狀態")),
                            new RichMenuArea(new RichMenuBounds(1667, 843, 833, 843), new MessageAction("幫助", "#幫助"))
                    ))
                    .build();

            RichMenuIdResponse response = messagingClient.createRichMenu(richMenu).join();
            String richMenuId = response.getRichMenuId();

            byte[] imageBytes = generateRichMenuImage();
            blobClient.setRichMenuImage(richMenuId, "image/png", imageBytes).join();
            messagingClient.setDefaultRichMenu(richMenuId).join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to register RichMenu", e);
        }
    }

    private byte[] generateRichMenuImage() {
        try {
            int width = 2500;
            int height = 1686;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(new Color(0xFFF0F5));
            g.fillRect(0, 0, width, height);

            String[][] buttons = {
                    {"啟用女友", "#FF69B4"},
                    {"提示詞", "#87CEEB"},
                    {"啟用語音", "#FFA500"},
                    {"關閉女友", "#FF6347"},
                    {"狀態", "#9370DB"},
                    {"幫助", "#90EE90"}
            };

            int cols = 3;
            int rows = 2;
            int btnW = width / cols;
            int btnH = height / rows;
            int margin = 20;

            g.setFont(new Font("SansSerif", Font.BOLD, 100));

            for (int i = 0; i < buttons.length; i++) {
                int col = i % cols;
                int row = i / cols;
                int x = col * btnW + margin;
                int y = row * btnH + margin;
                int bw = btnW - margin * 2;
                int bh = btnH - margin * 2;

                g.setColor(Color.decode(buttons[i][1]));
                g.fillRoundRect(x, y, bw, bh, 40, 40);

                g.setColor(Color.WHITE);
                FontMetrics fm = g.getFontMetrics();
                String label = buttons[i][0];
                int textX = x + (bw - fm.stringWidth(label)) / 2;
                int textY = y + (bh + fm.getHeight()) / 2 - fm.getDescent();
                g.drawString(label, textX, textY);
            }

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RichMenu image", e);
        }
    }
}
