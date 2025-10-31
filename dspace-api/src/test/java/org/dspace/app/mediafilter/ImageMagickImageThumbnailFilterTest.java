/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.mediafilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.dspace.AbstractUnitTest;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Ensure that image thumbnails are created correctly and that tiffs containing
 * multiple images are handled. The tests are marked ignore because they require
 * ImageMagick to be installed on the system running the tests.
 *
 * @author Mark Patton
 */
@Ignore
public class ImageMagickImageThumbnailFilterTest extends AbstractUnitTest {
    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private BufferedImage createSolidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();

        return image;
    }

    private File createTiff(int width, int height, Color color) throws Exception {
        BufferedImage image = createSolidImage(width, height, color);

        File file = File.createTempFile("test-image", ".tiff");
        file.deleteOnExit();
        ImageIO.write(image, "TIFF", file);

        return file;
    }

    private File createJpeg(int width, int height, Color color) throws Exception {
        BufferedImage image = createSolidImage(width, height, color);

        File file = File.createTempFile("test-image", ".jpg");
        file.deleteOnExit();
        ImageIO.write(image, "JPEG", file);

        return file;
    }

    private File createTiffWithTwoImages(int width, int height, Color color1, Color color2) throws Exception {
        File file = File.createTempFile("test-image", ".tiff");
        file.deleteOnExit();

        ImageWriter writer = null;
        try (ImageOutputStream os = ImageIO.createImageOutputStream(file)) {
            BufferedImage image1 = createSolidImage(width, height, color1);
            BufferedImage image2 = createSolidImage(width, height, color2);

            writer = ImageIO.getImageWritersByFormatName("TIFF").next();
            writer.setOutput(os);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(image1, null, null), writer.getDefaultWriteParam());
            writer.writeToSequence(new IIOImage(image2, null, null), writer.getDefaultWriteParam());
            writer.endWriteSequence();
        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }

        return file;
    }

    @Test
    public void testGetDestinationStreamSingleImageTiff() throws Exception {
        ImageMagickImageThumbnailFilter filter = new ImageMagickImageThumbnailFilter();

        File tiff_file = createTiff(400, 300, Color.WHITE);

        try (InputStream image_is = new FileInputStream(tiff_file)) {
            InputStream thumb_os = filter.getDestinationStream(null, image_is, false);

            BufferedImage thumb_image = ImageIO.read(thumb_os);

            int max_width = configurationService.getIntProperty("thumbnail.maxwidth", 200);
            int max_height = configurationService.getIntProperty("thumbnail.maxheight", 200);

            assertTrue(thumb_image.getWidth() <= max_width);
            assertTrue(thumb_image.getHeight() <= max_height);
            assertEquals(Color.WHITE.getRGB(), thumb_image.getRGB(0, 0));

        } finally {
            tiff_file.delete();
        }
    }

    @Test
    public void testGetDestinationStreamJpeg() throws Exception {
        ImageMagickImageThumbnailFilter filter = new ImageMagickImageThumbnailFilter();

        File jpeg_file = createJpeg(400, 300, Color.WHITE);

        try (InputStream image_is = new FileInputStream(jpeg_file)) {
            InputStream thumb_os = filter.getDestinationStream(null, image_is, false);

            BufferedImage thumb_image = ImageIO.read(thumb_os);

            int max_width = configurationService.getIntProperty("thumbnail.maxwidth", 200);
            int max_height = configurationService.getIntProperty("thumbnail.maxheight", 200);

            assertTrue(thumb_image.getWidth() <= max_width);
            assertTrue(thumb_image.getHeight() <= max_height);
            assertEquals(Color.WHITE.getRGB(), thumb_image.getRGB(0, 0));

        } finally {
            jpeg_file.delete();
        }
    }

    /**
     * Test that when a TIFF with multiple images is provided, the first image is used.
     * @throws Exception
     */
    @Test
    public void testGetDestinationStreamMultipleImageTif() throws Exception {
        ImageMagickImageThumbnailFilter filter = new ImageMagickImageThumbnailFilter();

        File tiff_file = createTiffWithTwoImages(400, 300, Color.BLACK, Color.WHITE);

        try (InputStream image_is = new FileInputStream(tiff_file)) {
            InputStream thumb_os = filter.getDestinationStream(null, image_is, false);

            BufferedImage thumb_image = ImageIO.read(thumb_os);

            int max_width = configurationService.getIntProperty("thumbnail.maxwidth", 200);
            int max_height = configurationService.getIntProperty("thumbnail.maxheight", 200);

            assertTrue(thumb_image.getWidth() <= max_width);
            assertTrue(thumb_image.getHeight() <= max_height);
            assertEquals(Color.BLACK.getRGB(), thumb_image.getRGB(0, 0));
        } finally {
            tiff_file.delete();
        }
    }
}
