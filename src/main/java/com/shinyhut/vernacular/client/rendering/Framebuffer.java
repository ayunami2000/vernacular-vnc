package com.shinyhut.vernacular.client.rendering;

import com.shinyhut.vernacular.client.VncSession;
import com.shinyhut.vernacular.client.exceptions.VncException;
import com.shinyhut.vernacular.client.rendering.renderers.*;
import com.shinyhut.vernacular.protocol.messages.*;
import com.shinyhut.vernacular.protocol.messages.Rectangle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.shinyhut.vernacular.protocol.messages.Encoding.*;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.time.LocalDateTime.now;

public class Framebuffer {

    private final VncSession session;
    private final Map<BigInteger, ColorMapEntry> colorMap = new ConcurrentHashMap<>();
    private final Map<Encoding, Renderer> renderers = new ConcurrentHashMap<>();

    private BufferedImage frame;

    public Framebuffer(VncSession session) {
        PixelDecoder pixelDecoder = new PixelDecoder(colorMap);
        RawRenderer rawRenderer = new RawRenderer(pixelDecoder);
        renderers.put(RAW, rawRenderer);
        renderers.put(COPYRECT, new CopyRectRenderer());
        renderers.put(RRE, new RRERenderer(pixelDecoder));
        renderers.put(HEXTILE, new HextileRenderer(rawRenderer, pixelDecoder));

        this.frame = new BufferedImage(session.getFramebufferWidth(), session.getFramebufferHeight(), TYPE_INT_RGB);
        this.frame.setAccelerationPriority(1);
        this.session = session;
    }

    public void processUpdate(FramebufferUpdate update) throws VncException {
        session.setLastFramebufferUpdateTime(now());
        for (Rectangle rectangle : update.getRectangles()) {
            if (rectangle.getEncoding() == DESKTOP_SIZE) {
                resizeFramebuffer(rectangle);
            } else {
                renderers.get(rectangle.getEncoding()).render(frame, rectangle, session.getPixelFormat());
            }
        }
        paint();
    }

    private void paint() {
        Consumer<Image> listener = session.getConfig().getFramebufferUpdateListener();
        if (listener != null) {
            listener.accept(frame);
        }
    }

    public void updateColorMap(SetColorMapEntries update) {
        for (int i = 0; i < update.getColors().size(); i++) {
            colorMap.put(BigInteger.valueOf(i + update.getFirstColor()), update.getColors().get(i));
        }
    }

    private void resizeFramebuffer(Rectangle newSize) {
        int width = newSize.getWidth();
        int height = newSize.getHeight();
        session.setFramebufferWidth(width);
        session.setFramebufferHeight(height);
        BufferedImage resized = new BufferedImage(width, height, TYPE_INT_RGB);
        resized.getGraphics().drawImage(frame, 0, 0, null);
        frame = resized;
    }

}
