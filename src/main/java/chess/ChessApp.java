package chess;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Application entry point; requires only the Java standard library. */
public final class ChessApp {
    private ChessApp() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--screenshot")) {
            Path output = Path.of(args[1]).toAbsolutePath();
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            SwingUtilities.invokeAndWait(() -> {
                applyLookAndFeel();
                ChessWindow content = new ChessWindow();
                content.setSize(1120, 820);
                layoutTree(content);
                BufferedImage image = new BufferedImage(1120, 820, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                content.printAll(g);
                g.dispose();
                try { ImageIO.write(image, "png", output.toFile()); }
                catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                content.close();
            });
            System.out.println("Saved board preview: " + output);
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: java -jar knightlight.jar [--screenshot path.png]");
            System.exit(2);
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Knightlight needs a desktop display. Use --screenshot path.png for a headless preview.");
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            ChessWindow content = new ChessWindow();
            JFrame frame = new JFrame("Knightlight | Java Chess");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(content);
            frame.setSize(1120, 860);
            frame.setMinimumSize(new Dimension(950, 740));
            frame.setLocationRelativeTo(null);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent event) { content.close(); }
            });
            frame.setVisible(true);
        });
    }

    private static void applyLookAndFeel() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) { /* The default Swing appearance remains fully usable. */ }
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) layoutTree(nested);
        }
    }
}
