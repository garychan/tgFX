/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tgfx.render;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import tgfx.Main;
import tgfx.SerialWriter;
import tgfx.tinyg.CommandManager;
import tgfx.tinyg.TinygDriver;

/**
 *
 * @author rileyporter
 */
public class CNCMachine extends Pane {
    public static  StackPane gcodePane = new StackPane(); //Holds CNCMachine
    private DecimalFormat df = new DecimalFormat("#.##");
    private final Circle cursorPoint = new Circle(2, javafx.scene.paint.Color.RED);
    private static double xPrevious;
    private static double yPrevious;
    private double magnification = 1;
    private static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CNCMachine.class);      
    
    public CNCMachine() {
        this.setVisible(false);
        this.setPadding(new Insets(10));


        /*####################################
         *CSS
         #################################### */
        this.setStyle("-fx-background-color: black; -fx-border-color: orange;  -fx-border-width: .5;");

        /*####################################
         *Cursor Set
         #################################### */

        //Cursor point indicator
        cursorPoint.setRadius(1);
        cursorPoint.translateYProperty().bind(this.heightProperty().subtract(TinygDriver.getInstance().m.getAxisByName("y").getMachinePositionSimple()));
        cursorPoint.layoutXProperty().bind(TinygDriver.getInstance().m.getAxisByName("x").getMachinePositionSimple());

//        cncMachine.getHeight() - TinygDriver.getInstance().m.getAxisByName("y").getMachinePosition().get();
        this.setMaxSize(0, 0);  //hide this element until we connect
        //Set our machine size from tinyg travel max

        this.maxHeightProperty().bind(TinygDriver.getInstance().m.getAxisByName("y").getTravelMaxSimple().multiply(TinygDriver.getInstance().m.gcodeUnitDivision));
        this.maxWidthProperty().bind(TinygDriver.getInstance().m.getAxisByName("x").getTravelMaxSimple().multiply(TinygDriver.getInstance().m.gcodeUnitDivision));

        final Circle c = new Circle(2, Color.RED);

        final Text cursorText = new Text("None");
//        cursorText.setStroke(Color.YELLOW);
        cursorText.setFill(Color.YELLOW);
        cursorText.setFont(Font.font("Arial", 6));

        
        
        setupLayout(); //initial layout setup in constructor
        
        
        /*####################################
         *Bindings
         *#################################### */
//
//        this.scaleXProperty().bind(this.widthProperty().subtract(tgfx.ui.GcodeTabController.gcodePane.widthProperty()));
//        this.scaleYProperty().bind(tgfx.ui.GcodeTabController.gcodePane.heightProperty().subtract(this.heightProperty().multiply(.9)));


        this.setOnMouseExited(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent me) {
//                gcodePane.getChildren().remove(c);
                getChildren().remove(cursorText);

            }
        });







        this.setOnMouseEntered(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent me) {
//                gcodePane.getChildren().remove(c);
                getChildren().add(cursorText);

            }
        });

        this.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent me) {
                cursorText.setText("(X: " + getNormalizedXasString(me.getX()) + ")\n(Y: " + getNormalizedYasString(me.getY()) + ")");
//                 cursorText.setText("(X: " + df.format(me.getX() / TinygDriver.getInstance().m.gcodeUnitDivision.get())
//                        + ")\n(Y: " + df.format((getHeight() - me.getY()) / TinygDriver.getInstance().m.gcodeUnitDivision.get()) + ")");
                cursorText.setX(me.getX() + 10);
                cursorText.setY(me.getY());

            }
        });





        this.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(final MouseEvent me) {
                //This is so we can set our machine position when a machine does not have homing switches
                if (me.getButton().equals(MouseButton.SECONDARY)) {
                    //Right Clicked
                    ContextMenu cm = new ContextMenu();
                    MenuItem menuItem1 = new MenuItem("Set Machine Position");
                    menuItem1.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent t) {
                            Draw2d.setFirstDraw(true); //We do not want to draw a line from our previous position
                            TinygDriver.getInstance().cmdManager.setMachinePosition(getNormalizedX(me.getX()), getNormalizedY(me.getY()));
                            Draw2d.setFirstDraw(true); //This allows us to move our drawing to a new place without drawing a line from the old.
                            try {
                                TinygDriver.getInstance().write(CommandManager.CMD_APPLY_SYSTEM_ZERO_ALL_AXES);
                                TinygDriver.getInstance().write(CommandManager.CMD_QUERY_STATUS_REPORT);
                            } catch (Exception ex) {
                                Logger.getLogger(CNCMachine.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            //G92 does not invoke a status report... So we need to generate one to have
                            //Our GUI update the coordinates to zero


                        }
                    });
                    cm.getItems().add(menuItem1);
                    cm.show((Node) me.getSource(), me.getScreenX(), me.getScreenY());
                }

            }
        });



    }

    public double getNormalizedX(double x) {
        return (Double.valueOf((x / TinygDriver.getInstance().m.gcodeUnitDivision.get())));
    }

    public double getNormalizedY(double y) {
        return (Double.valueOf((getHeight() - y) / TinygDriver.getInstance().m.gcodeUnitDivision.get()));
    }

    public String getNormalizedYasString(double y) {
        return (df.format(getNormalizedY(y)));
    }

    public String getNormalizedXasString(double x) {
        return (df.format(getNormalizedX(x)));
    }

    public boolean checkBoundsY(Line l) {
        if ((this.getHeight() - l.getEndY()) >= 0 && (this.getHeight() - l.getEndY()) <= this.getHeight() + 1) {
            return true;
        } else {
            return false;
        }
    }

    public boolean checkBoundsX(Line l) {
        if (l.getEndX() >= 0 && l.getEndX() <= this.getWidth()) {
            return true;
        } else {
            return false;
        }
    }

    public void clearScreen() {
        this.getChildren().clear();
        Draw2d.setFirstDraw(true);  //We don't want to draw a line from where the previous point was when a clear screen is called.
        setupLayout();  //re-draw the needed elements.
    }

    /**
     *
     * @param moveType
     * @param vel
     */
    public void drawLine(String moveType, double vel) {
        Line l;
        l = new Line();
        l.setSmooth(true);
        //Code to make mm's look the same size as inches
        double scale = 1;
        double unitMagnication = 1;

//        if (TinygDriver.getInstance().m.getGcodeUnitMode().get().equals(Gcode_unit_modes.inches.toString())) {
//            unitMagnication = 5;  //INCHES
//        } else {
//            unitMagnication = 2; //MM
//        }
//        double newX = unitMagnication * (Double.valueOf(TinygDriver.getInstance().m.getAxisByName("X").getWork_position().get()) + 80);// + magnification;
//        double newY = unitMagnication * (Double.valueOf(TinygDriver.getInstance().m.getAxisByName("Y").getWork_position().get()) + 80);// + magnification;


//        if (newX > gcodePane.getWidth() || newX > gcodePane.getWidth()) {
//            scale = scale / 2;
//            Line line = new Line();
//            Iterator ii = gcodePane.getChildren().iterator();
//            gcodePane.getChildren().clear(); //remove them after we have the iterator
//            while (ii.hasNext()) {
//                if (ii.next().getClass().toString().contains("Line")) {
//                    //This is a line.
//                    line = (Line) ii.next();
//                    line.setStartX(line.getStartX() / 2);
//                    line.setStartY(line.getStartY() / 2);
//                    line.setEndX(line.getEndX() / 2);
//                    line.setEndY(line.getEndY() / 2);
//                    gcodePane.getChildren().add(line);
//
//                }

//            }
//            console.appendText("[+]Finished Drawing Prevew Scale Change.\n");
//            gcodeWindow.setScaleX(scale);
//            gcodeWindow.setScaleY(scale);
//        }
//        System.out.println(gcodePane.getHeight() - TinygDriver.getInstance().m.getAxisByName("y").getWork_position().get());
        double newX = TinygDriver.getInstance().m.getAxisByName("x").getMachinePositionSimple().get();// + magnification;
        double newY = this.getHeight() - TinygDriver.getInstance().m.getAxisByName("y").getMachinePositionSimple().get();//(gcodePane.getHeight() - (Double.valueOf(TinygDriver.getInstance().m.getAxisByName("y").getWork_position().get())));// + magnification;

        if (Draw2d.isFirstDraw()) {
            //This is to not have us draw a line on the first connect.
            l = new Line(newX, this.getHeight(), newX, this.getHeight());
            Draw2d.setFirstDraw(false);
        } else {
            l = new Line(xPrevious, yPrevious, newX, newY);
            l.setStrokeWidth(.5);
        }


        if (TinygDriver.getInstance().m.getMotionMode().get().equals("traverse")) {
            //G0 Moves
            l.getStrokeDashArray().addAll(1d, 5d);
            l.setStroke(Draw2d.TRAVERSE);
        } else {
//            l.setStroke(Draw2d.getLineColorFromVelocity(vel));
            l.setStroke(Draw2d.FAST);
        }

        xPrevious = newX;
        yPrevious = newY; //TODO Pull these out to CNC machine or Draw2d these are out of place

        if (l != null) {
            if (this.checkBoundsX(l) && this.checkBoundsY(l)) {
                //Line is withing the travel max gcode preview box.  So we will draw it.
                this.getChildren().add(l);  //Add the line to the Pane 
            } else {
                Logger.getLogger("Main").info("Outside of Bounds X");
//                Main.postConsoleMessage("WARNING: Outside tool outside work area. X=" + l.getEndX() + " Y=" + (this.getHeight() - l.getEndY()) + " Home your machine or preform a reset.\n");
            }
        }
    }

    public void zeroSystem() {
        if (TinygDriver.getInstance().isConnected()) {
            try {
                Draw2d.setFirstDraw(true); //This allows us to move our drawing to a new place without drawing a line from the old.
                TinygDriver.getInstance().write(CommandManager.CMD_APPLY_SYSTEM_ZERO_ALL_AXES);
                //G92 does not invoke a status report... So we need to generate one to have
                //Our GUI update the coordinates to zero
                TinygDriver.getInstance().write(CommandManager.CMD_QUERY_STATUS_REPORT);
                //We need to set these to 0 so we do not draw a line from the last place we were to 0,0
                resetDrawingCoords();
            } catch (Exception ex) {
            }
        }
    }

    public static void resetDrawingCoords() {
        //After a reset has occured we call this ot reset the previous coords.
        xPrevious = 0;
        yPrevious = 0;
    }

    private void setupLayout() {
        //This draws the x axis text as well as grid etc
        Text xText = new Text("X Axis");
        Text yText = new Text("Y Axis");

        xText.setY(-10);
        xText.xProperty().bind(this.heightProperty().divide(2));
        xText.setRotate(0);
        xText.setFill(Color.YELLOW);
        xText.setFont(Font.font("Arial", 10));

        yText.setX(-25);
        yText.yProperty().bind(this.widthProperty().divide(2));
        yText.setRotate(-90);
        yText.setFill(Color.YELLOW);
        yText.setFont(Font.font("Arial", 10));

        this.getChildren().add(xText);
        this.getChildren().add(yText);

        this.setCursor(Cursor.CROSSHAIR);
        this.getChildren().add(cursorPoint);
    }

    public void autoScaleWorkTravelSpace(double scaleAmount) {

        //Get the axis with the smallest available space.  Think aspect ratio really


        double stroke = 2 / scaleAmount;
        this.setScaleX(scaleAmount);
        this.setScaleY(scaleAmount);
        Iterator ii = this.getChildren().iterator();

        while (ii.hasNext()) {
            if (ii.next().getClass().getName().endsWith("Line")) {
                Line l = (Line) ii.next();
                l.setStrokeWidth(stroke);
            }
        }
    }
}
