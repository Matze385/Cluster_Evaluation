/*
 * To the extent possible under law, the Fiji developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import ij.IJ;
import ij.ImageStack;
import ij.ImageJ;
import ij.ImagePlus;
//import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
//import ij.gui.WaitForUserDialog;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.Point;
import java.awt.Color;

/**
 * ProcessPixels
 *
 * A template for annotating ground truth for cluster resolving 
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
 *
 * @author The Fiji Team
 */
public class Cluster_Ground_Truth_Generation implements PlugInFilter, MouseListener {
	protected ImagePlus image;
        protected ImageCanvas canvas;

	// image/stack property members
	private int width;
	private int height;
        private int interval;
    
        /*-------------------
        Cluster information
        -------------------*/
        //chosen interval
        private boolean intervalSet = false;
        private int startFrame;
        private int endFrame;
        
        // Region of interest
        private Roi roi;
        private boolean roiSelected = false;

        //save click events
        private boolean click = false;
        private Point clickPoint = new Point();       

	// plugin parameters
	public String name;

	/**
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
                ImageStack stack = image.getStack();
                interval = stack.getSize();
                ImageProcessor ip = stack.getProcessor(interval);
                width = ip.getWidth();
                height = ip.getHeight();
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
	}

	/**
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip) {
                
		// get width and height
		//width = ip.getWidth();
		//height = ip.getHeight();
                                
                //set the interval for a cluster
		while (!intervalSet) {
                        showDialog();
		}
            
                ImageWindow win = image.getWindow();
                canvas = win.getCanvas();
                canvas.addMouseListener(this);
                
                //select roi
                while(!roiSelected){
                        selectRoi(ip);
                }
                
                Color color = new Color(255,255,255);
                drawRoi(ip, 5, color);
                image.updateAndRepaintWindow();
                
	}
        
        private boolean selectRoi(ImageProcessor ip){
                //WaitForUserDialog wait_dialog = new WaitForUserDialog("Select a rectangle as region of interest! Then click OK.");
                //wait_dialog.show();
                IJ.write("Select rectangle as region of interest: Click on position for upper left corner!");
                waitForClick();
                int xRec = clickPoint.x;
                int yRec = clickPoint.y;
                IJ.write("Select rectangle as region of interest: Click on position for lower right corner!");
                waitForClick();
                int width = clickPoint.x - xRec;
                int height = clickPoint.y - yRec;
                if (width < 0 || width>this.width){
                        IJ.showMessage("Invalid width for rectangle. Annotate a new rectangle!");
                        return false;
                }
                if (height < 0 || height>this.height){
                        IJ.showMessage("Invalid width for rectangle. Annotate a new rectangle!");
                        return false;
                }
                roi = new Roi(xRec, yRec, width, height);
                roiSelected = true;
                return true;
        }
        
        private void drawRoi(ImageProcessor ip, int lineWidth, Color lineColor){
                if(roiSelected){
                        ip.setLineWidth(lineWidth);
                        ip.setColor(lineColor);
                        ip.draw(roi);
                } else {
                        IJ.showMessage("No rectangle found");
                        selectRoi(ip);
                        drawRoi(ip, lineWidth, lineColor);
                }
        }


	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("Assign start and end frame");

		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("start frame", 0, 0);
		gd.addNumericField("end frame", 0, 0);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		startFrame = (int) gd.getNextNumber();
		endFrame = (int) gd.getNextNumber();
                // check that start and end frame in correct range
                if (startFrame < 0){
                        IJ.showMessage("start_frame out of allowed range [1," + (interval-1) + "]. Correct the input.");
                        return false;
                }
                if (endFrame > interval){
                        IJ.showMessage("end_frame out of allowed range [2," + interval + "]. Correct the input." );
                        return false;
                }
                if (startFrame >= endFrame){
                        IJ.showMessage("start_frame must be smaller than end_frame. Correct the input." );
                        return false;
                }
                intervalSet = true;
		return true;
	}

	/**
	 * Process an image.
	 *
	 * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does require it;
	 * the method {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only
	 * handle 2-dimensional data.
	 *
	 * If your plugin does not change the pixels in-place, make this method return the results and
	 * change the {@link #setup(java.lang.String, ij.ImagePlus)} method to return also the
	 * <i>DOES_NOTHING</i> flag.
	 *
	 * @param image the image (possible multi-dimensional)
	 */
        /*
	public void process(ImagePlus image) {
		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= image.getStackSize(); i++)
			process(image.getStack().getProcessor(i));
	}

	// Select processing method depending on image type
	public void process(ImageProcessor ip) {
		int type = image.getType();
		if (type == ImagePlus.GRAY8)
			process( (byte[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY16)
			process( (short[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY32)
			process( (float[]) ip.getPixels() );
		else if (type == ImagePlus.COLOR_RGB)
			process( (int[]) ip.getPixels() );
		else {
			throw new RuntimeException("not supported");
		}
	}

	// processing of GRAY8 images
	public void process(byte[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (byte)value;
			}
		}
	}

	// processing of GRAY16 images
	public void process(short[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (short)value;
			}
		}
	}

	// processing of GRAY32 images
	public void process(float[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (float)value;
			}
		}
	}

	// processing of COLOR_RGB images
	public void process(int[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (int)value;
			}
		}
	}
        */

	public void showAbout() {
		IJ.showMessage("ProcessPixels",
			"a template for processing each pixel of an image"
		);
	}
        

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads an
	 * image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = Cluster_Ground_Truth_Generation.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
    
        public void waitForClick() {
                while(!click) IJ.wait(300);
                click = false;
        }

        //Override methods in MouseListener
	@Override
        public void mouseClicked(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                int offscreenX = canvas.offScreenX(x);
                int offscreenY = canvas.offScreenY(y);
                click = true;
                clickPoint.x = offscreenX;
                clickPoint.y = offscreenY;
                IJ.write("mousePressed: " + offscreenX + ", " + offscreenY );
        }
            
	@Override
        public void mousePressed(MouseEvent e) {}
        
	@Override
        public void mouseReleased(MouseEvent e) {}
        
	@Override
        public void mouseEntered(MouseEvent e) {}

	@Override
        public void mouseExited(MouseEvent e) {}


}
