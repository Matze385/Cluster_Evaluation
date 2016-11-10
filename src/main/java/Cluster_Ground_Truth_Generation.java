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
import java.awt.Scrollbar;
import java.awt.Panel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.io.Writer;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
	protected ImageStack stack;
        protected ImageCanvas canvas;
        protected IW  win;
  
        //list of list for saving all ground truth information in format [[startFrame, endFrame, xCen, yCen, radius, nObjects, xStart1, yStart1, xEnd1, yEnd1, xStart2,..],...]
        List<List<Integer>> groundTruth = new ArrayList<List<Integer>>();
        //Overlay of circles for cropping out data
        protected Overlay roiOverlay;
        private boolean finished = false;

	// image/stack property members
	private int width;
	private int height;
        private int interval;
    
        /*-------------------
        current Cluster information
        -------------------*/
        //chosen interval
        private boolean intervalSet = false;
        private int startFrame;
        private Overlay startOverlay;
        private int endFrame;
        private Overlay endOverlay;
        
        //Objects
        private boolean nObjectsSet = false;
        private int nObjects;

        // Region of interest
        private Roi roi;
        private boolean roiSelected = false;
        private int xCen;
        private int yCen;
        private int radius;

        //current Object properties
        private int xObjCen;
        private int yObjCen;
        private boolean idSet=false;
        private int id;
        private int[] setObjectIds;
        
        //cluster free for saving
        private boolean clusterConfirmed = false;

        //radius for finding best hypo in first and last frame
        private int iniRadius;
        private boolean iniRadiusSelected = false;

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
                stack = image.getStack();
                interval = stack.getSize();
                width = stack.getWidth();
                height = stack.getHeight();
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB | STACK_REQUIRED;
	}

	/**
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip) {
                
                win = new IW(image);
                canvas = win.getCanvas();
                canvas.addMouseListener(this);
                //select radius for finding best hypothesis
                while(!iniRadiusSelected){
                        showDialogObjectFindRadius();
                }
                //generate more clusters as long as you want
                while(!finished){
                        List<Integer> iCluster = new ArrayList<Integer>();
                        //set the interval for a cluster
                        while (!intervalSet) {
                                showDialogTimeInterval();
                        }
                        intervalSet = false;
                        iCluster.add(startFrame);
                        iCluster.add(endFrame);
                        
                                       
                        //select roi
                        while(!roiSelected){
                                selectRoi(ip);
                        }
                        roiSelected = false;
                        iCluster.add(xCen);
                        iCluster.add(yCen);
                        iCluster.add(radius);                   
    
                        Color color = new Color(255,255,255);
                        roiOverlay = new Overlay();
                        roi.setStrokeWidth(5);
                        roiOverlay.add(roi);
                        roiOverlay.setStrokeColor(color);
                        //roiOverlay.crop(startFrame+1, endFrame);
                        startOverlay = new Overlay();
                        startOverlay = roiOverlay.duplicate();
                        startOverlay.setLabelColor(color);
                        startOverlay.setStrokeColor(color);
                        endOverlay = new Overlay();
                        endOverlay = roiOverlay.duplicate();
                        endOverlay.setLabelColor(color);
                        endOverlay.setStrokeColor(color);
                        endOverlay.drawNames(true);

                        image.updateAndRepaintWindow();
                    
                        //select number of objects and initialize
                        while(!nObjectsSet){
                                showDialogNumberObjects();
                        }
                        nObjectsSet = false;
                        iCluster.add(nObjects);

                        //save coordinates in format [objectId-1][x/y]
                        int[][] coordStart = new int[nObjects][2];
                        //click on objects in start frame
                        for(int iObject=0; iObject<nObjects; iObject +=1){
                                Roi iniRoi = selectObject("select center of object in start frame");
                                //int xObj = (int) iniRoi.x + iniRoi.width/2;
                                //nt yObj = (int) iniRoi.y + iniRoi.height/2;
                                iniRoi.setStrokeWidth(3);
                                iniRoi.setStrokeColor(color);
                                coordStart[iObject][0] = xObjCen;
                                coordStart[iObject][1] = yObjCen;
                                Roi iniTextRoi = new TextRoi(xObjCen - iniRadius/2, yObjCen - iniRadius, "" + (iObject+1));
                                iniTextRoi.setStrokeColor(color);
                                startOverlay.add(iniRoi);
                                startOverlay.add(iniTextRoi);
                                image.updateAndRepaintWindow();
                        }
                        
                        int[][] coordEnd = new int[nObjects][2];
                        //create setObjectIds array and initialize with zeros
                        setObjectIds = new int[nObjects];
                        for(int i=0; i<nObjects; i +=1){
                                setObjectIds[i] = 0;
                        }
                        //select objects in end frame and assign correct id
                        for(int iObject=0; iObject<nObjects; iObject +=1){
                                Roi iniRoi = selectObject("Select center of object in end frame");
                                iniRoi.setStrokeWidth(3);
                                iniRoi.setStrokeColor(color);
                                endOverlay.add(iniRoi);
                                image.updateAndRepaintWindow();
                                if(!(iObject==nObjects-1)){
                                        while(!idSet){
                                                showDialogObjectId();
                                        }
                                        idSet = false;
                                } else {
                                        int deducedDefaultId = 1;
                                        for(int defaultId =1; defaultId<=nObjects; defaultId += 1){
                                                if(idFound(defaultId, setObjectIds) == false){
                                                        deducedDefaultId = defaultId;
                                                        break;
                                                }
                                        }
                                        id = deducedDefaultId;   
                                }
                                setObjectIds[iObject] = id;
                                coordEnd[id-1][0] = xObjCen;
                                coordEnd[id-1][1] = yObjCen;
                                Roi iniTextRoi = new TextRoi(xObjCen - iniRadius/2, yObjCen - iniRadius, "" + id);
                                iniTextRoi.setStrokeColor(color);
                                endOverlay.add(iniTextRoi);
                                image.updateAndRepaintWindow();
                                
                        }
                        for(int iObject=0; iObject<nObjects; iObject +=1){
                                iCluster.add(coordStart[iObject][0]);
                                iCluster.add(coordStart[iObject][1]);
                                iCluster.add(coordEnd[iObject][0]);
                                iCluster.add(coordEnd[iObject][1]);
                        }
                        
                        showDialogConfirmCluster();
                        if (clusterConfirmed == true){
                                groundTruth.add(iCluster);
                                clusterConfirmed = false;
                        }
                        roiOverlay.clear();
                        startOverlay.clear();
                        endOverlay.clear();
                        image.updateAndRepaintWindow();
                        showDialogFinish();
                }
                try {
                        printJson(groundTruth, "clusterGroundTruth.json");
                } catch (IOException e) {
                        IJ.showMessage("Saving ground truth in json format does not work!");
                }
	}

        private void printJson(List<List<Integer>> groundTruth, java.lang.String fileName) throws IOException{
                Gson gson = new Gson();
                Type listOfTestObject = new TypeToken<List<List<Integer>>>(){}.getType();

                //Make Serial 
                Writer osWriter = new FileWriter("/mnt/sdc1/mbrosowsky/Fiji_plugin/" + fileName);
                /*
                List<TestObject> list = Collections.synchronizedList(new ArrayList<TestObject>() );
                list.add(new TestObject());
                list.add(new TestObject());
                list.add(new TestObject());
                list.add(new TestObject());*/
                java.lang.String s = gson.toJson(groundTruth, listOfTestObject);
                osWriter.write(s);
                osWriter.close();
        }

        private boolean selectRoi(ImageProcessor ip){
                IJ.showMessage("Select circle as region of interest: Click on position for center!");
                waitForClick();
                xCen = clickPoint.x;
                yCen = clickPoint.y;
                //IJ.showMessage("Select rectangle as region of interest: Click on position for lower right corner!");
                IJ.showMessage("Select circle as region of interest: Click on margin!");
                waitForClick();
                radius = (int) Math.pow(Math.pow((clickPoint.x - xCen),2.) + Math.pow(clickPoint.y - yCen ,2.), 0.5);
                
                /*
                if (width < 0 || width>this.width){
                        IJ.showMessage("Invalid width for rectangle. Annotate a new rectangle!");
                        return false;
                }
                if (height < 0 || height>this.height){
                        IJ.showMessage("Invalid width for rectangle. Annotate a new rectangle!");
                        return false;
                }
                */
                roi = new OvalRoi(xCen - radius, yCen - radius, 2*radius, 2*radius);
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
        
        private Roi selectObject(java.lang.String s){
                IJ.showMessage(s);
                waitForClick();
                xObjCen = clickPoint.x;
                yObjCen = clickPoint.y;
                
                /*
                if (width < 0 || width>this.width){
                        IJ.showMessage("Invalid width for rectangle. Annotate a new rectangle!");
                        return false;
                }
                if (height < 0 || height>this.height){
                        IJ.showMessage("Invalid width for rectangle. Annotate a new rectangle!");
                        return false;
                }
                */
                Roi iniRoi = new OvalRoi(xObjCen - iniRadius, yObjCen - iniRadius, 2*iniRadius, 2*iniRadius);
                return iniRoi;
        }

	private boolean showDialogTimeInterval() {
		GenericDialog gd = new NonBlockingGenericDialog("Assign start and end frame");

		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("start frame", 1, 0);
		gd.addNumericField("end frame", interval, 0);

		gd.showDialog();
                
		if (gd.wasCanceled())
			return false;

		// get entered values
		startFrame = (int) gd.getNextNumber();
		endFrame = (int) gd.getNextNumber();
                // check that start and end frame in correct range
                if (startFrame < 1){
                        IJ.showMessage("start frame out of allowed range [1," + (interval-1) + "]. Correct the input.");
                        return false;
                }
                if (endFrame > interval){
                        IJ.showMessage("end frame out of allowed range [2," + interval + "]. Correct the input." );
                        return false;
                }
                if (startFrame >= endFrame){
                        IJ.showMessage("start frame must be smaller than end_frame. Correct the input." );
                        return false;
                }
                startFrame -= 1;
                intervalSet = true;
		return true;
	}

        private boolean showDialogNumberObjects() {
		GenericDialog gd = new NonBlockingGenericDialog("Choose number of objects");

		// default value is 2, 2 digits right of the decimal point
		gd.addNumericField("number of objects", 2, 0);

		gd.showDialog();
                
		if (gd.wasCanceled())
			return false;

		// get entered values
		nObjects = (int) gd.getNextNumber();
                // check that start and end frame in correct range
                if (nObjects < 0){
                        IJ.showMessage("number of objects smaller than zero. Correct the input.");
                        return false;
                }
                if (nObjects > 20){
                        IJ.showMessage("maximal number of objects is 20. Correct the input.");
                        return false;
                }
                nObjectsSet = true;
		return true;
	}

        
        private boolean showDialogObjectFindRadius() {
		GenericDialog gd = new NonBlockingGenericDialog("Set radius for finding hypotheses in start and end frame");

		// default value is 2, 2 digits right of the decimal point
		gd.addNumericField("radius for finding best hypothesis in px", 10., 0);

		gd.showDialog();
                
		if (gd.wasCanceled())
			return false;

		// get entered values
		iniRadius = (int) gd.getNextNumber();
                // check that start and end frame in correct range
                if (iniRadius < 0){
                        IJ.showMessage("radius must be bigger than zero. Correct the input.");
                        return false;
                }
                iniRadiusSelected = true;
		return true;
	}

        private void showDialogConfirmCluster() {
		GenericDialog gd = new NonBlockingGenericDialog("Labeled Cluster confirmation");
                java.lang.String[] choice = new java.lang.String[2];
                choice[0] = "yes";
                choice[1] = "no";
		gd.addChoice("add labeled cluster to ground truth", choice, "yes");
		gd.showDialog();
                java.lang.String type = gd.getNextChoice();
                if (type=="yes"){
		// get entered values
		        clusterConfirmed = true;
                } else {
                        clusterConfirmed = false;
                }
	}
        
        private void showDialogFinish() {
		GenericDialog gd = new NonBlockingGenericDialog("finish Labeling");
                java.lang.String[] choice = new java.lang.String[2];
                choice[0] = "add Cluster";
                choice[1] = "finish";
		gd.addChoice("finish labeling", choice, "add Cluster");
		gd.showDialog();
                java.lang.String type = gd.getNextChoice();
                if (type=="finish"){
		// get entered values
		        finished = true;
                }
	}
        
        //returns true if objectId is in objectIds
        private boolean idFound(int objectId, int[] objectIds){
                boolean idFound = false;
                        for(int i=0; i<objectIds.length; i += 1){
                                if(objectId == objectIds[i]){
                                        idFound = true;
                                        break;
                                }
                        }
                return idFound;
        }
        
        private void showDialogObjectId() {
		GenericDialog gd = new NonBlockingGenericDialog("Set Object Id");
                int deducedDefaultId = 1;
                for(int defaultId =1; defaultId<=nObjects; defaultId += 1){
                        if(idFound(defaultId, setObjectIds) == false){
                                deducedDefaultId = defaultId;
                                break;
                        }
                }
		gd.addNumericField("Object Id", deducedDefaultId, 0);
		gd.showDialog();
                int[] possibleIds = new int[nObjects];
                for(int i=0; i<possibleIds.length; i += 1){
                        possibleIds[i] = i+1;
                }
                id = (int)gd.getNextNumber();
                if(idFound(id, possibleIds) && !idFound(id, setObjectIds)){
                    idSet = true;
                }
	}
	/**
	 * Process an image.getImageProcessor
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
                while(!click) {
                        IJ.wait(300);
                }
                click = false;
        }

        /*
        //Override methods in AdjustmentListener
         public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
                
                int z = sliceSelector.getValue();
                int slice = image.getCurrentSlice();
                IJ.log("Slicenr: " + slice);
                image.setSlice(z);
                canvas.setImageUpdated();
                canvas.repaint();
                //image.updateAndRepaintWindow();
                win.updateSliceSelector();
        } 
        */

        

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

        class IW extends ImageWindow implements AdjustmentListener{

                private Scrollbar sliceSelector;

                public IW(ImagePlus imp) {
                        super(imp);
                        remove(ic);                                        //remove the canvas in order to add it again with the new LayoutManager
                        setLayout(new BorderLayout());
                        add(ic, BorderLayout.CENTER);

                        // The scrollbar
                        int stackSize = imp.getStackSize();
                        sliceSelector = new Scrollbar();
                        sliceSelector.addAdjustmentListener(this);
                        sliceSelector.setFocusable(false);
                        sliceSelector.setMinimum(1);
                        sliceSelector.setMaximum(stackSize + 1);
                        sliceSelector.setOrientation(Scrollbar.HORIZONTAL);
                        sliceSelector.setVisible(true);
                        sliceSelector.setVisibleAmount(1);
                        add(sliceSelector, BorderLayout.SOUTH);
                        setVisible(true);
                }

                public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
                        int z = sliceSelector.getValue();
                        imp.setSlice(z);
                        //hide overlay when it is out of considered time interval
                        /*
                        if (z<startFrame+1 || z>endFrame){
                                imp.setHideOverlay(true);
                        } else {
                                imp.setHideOverlay(false);
                        }*/
                        if (z == startFrame+1){
                                imp.setOverlay(startOverlay); 
                        } else if (z == endFrame) {
                                imp.setOverlay(endOverlay);
                        } else if (z<startFrame+1 || z>endFrame) {
                                imp.setHideOverlay(true);
                        } else {
                                imp.setOverlay(roiOverlay);
                        }
                }
        }

 
}
