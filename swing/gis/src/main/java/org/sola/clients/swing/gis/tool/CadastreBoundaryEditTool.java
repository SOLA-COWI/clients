/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sola.clients.swing.gis.tool;

import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import java.util.Collection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.Geometries;
import org.geotools.map.extended.layer.ExtendedLayerGraphics;
import org.geotools.swing.extended.util.Messaging;
import org.geotools.swing.mapaction.extended.ExtendedAction;
import org.geotools.swing.tool.extended.ExtendedDrawToolWithSnapping;
import org.geotools.swing.tool.extended.ExtendedPan;
import org.opengis.feature.simple.SimpleFeature;
import org.sola.clients.swing.gis.layer.CadastreBoundaryPointLayer;
import org.sola.common.messaging.GisMessage;
import org.sola.common.messaging.MessageUtility;

/**
 * It handles the editing of a selected boundary. The boundary is already defined 
 * by the points found in the pointLayer.
 * Do not forget to set the targetLayer as well.
 * @author Elton Manoku
 */
public class CadastreBoundaryEditTool extends ExtendedDrawToolWithSnapping {

    public final static String NAME = "cadastre-boundary-edit";
    private String toolTip = MessageUtility.getLocalizedMessage(
            GisMessage.CADASTRE_BOUNDARY_EDIT_TOOL_TOOLTIP).getMessage();
    private ExtendedLayerGraphics targetLayer;
    private CadastreBoundaryPointLayer pointLayer;

    public CadastreBoundaryEditTool(CadastreBoundaryPointLayer pointLayer) {
        this.setToolName(NAME);
        this.setGeometryType(Geometries.LINESTRING);
        this.setToolTip(toolTip);
        this.pointLayer = pointLayer;
        this.getTargetSnappingLayers().add(this.pointLayer);
    }

    public void setTargetLayer(ExtendedLayerGraphics targetLayer) {
        this.targetLayer = targetLayer;
    }

    @Override
    protected void treatFinalizedGeometry(Geometry geometry) throws Exception {
        LineString targetBoundary = this.pointLayer.getTargetBoundary();
        if (targetBoundary == null) {
            return;
        }
        //Check if the new boundary starts and ends where the target boundary starts and ends
        LineString newBoundary = (LineString) geometry;
        if (!targetBoundary.getStartPoint().equals(newBoundary.getStartPoint())
                || !targetBoundary.getEndPoint().equals(newBoundary.getEndPoint())) {
            Messaging.getInstance().show(GisMessage.CADASTRE_BOUNDARY_NEW_MUST_START_END_AS_TARGET);
            return;
        }
        SimpleFeatureIterator iterator =
                (SimpleFeatureIterator) this.targetLayer.getFeatureCollection().features();
        while (iterator.hasNext()) {
            SimpleFeature currentFeature = iterator.next();
            Polygon currentGeom = (Polygon) currentFeature.getDefaultGeometry();
            if (!this.cadastralObjectHasTargetBoundary(currentGeom, targetBoundary)) {
                continue;
            }
            currentGeom = this.getPolygonModifiedBoundary(currentGeom, targetBoundary, newBoundary);
            this.targetLayer.replaceFeatureGeometry(currentFeature, currentGeom);
        }
        iterator.close();
        this.getSelectTool().clearSelection();
        this.getMapControl().setActiveTool(ExtendedPan.NAME);
    }

    private Polygon getPolygonModifiedBoundary(
            Polygon targetPolygon, LineString targetBoundary, LineString newBoundary) {
        LinearRing exteriorRing = this.getModifiedRing(
                targetPolygon.getExteriorRing(), targetBoundary, newBoundary);
        LinearRing[] interiorRings = new LinearRing[targetPolygon.getNumInteriorRing()];
        for (int ringInd = 0; ringInd < targetPolygon.getNumInteriorRing(); ringInd++) {
            interiorRings[ringInd] = this.getModifiedRing(
                    targetPolygon.getInteriorRingN(ringInd), targetBoundary, newBoundary);
        }
        return targetPolygon.getFactory().createPolygon(exteriorRing, interiorRings);
    }

    private LinearRing getModifiedRing(
            LineString ring, LineString targetBoundary, LineString newBoundary) {
        if (!ring.intersects(targetBoundary)) {
            return ring.getFactory().createLinearRing(ring.getCoordinateSequence());
        }
        Geometry leftPart = ring.difference(targetBoundary);
        LineMerger lineMerger = new LineMerger();
        lineMerger.add(leftPart);
        lineMerger.add(newBoundary);
        Collection result = lineMerger.getMergedLineStrings();
        CoordinateList coordList = new CoordinateList(ring.getCoordinates());
        for (Object linePart : result.toArray()) {
            coordList = new CoordinateList(((LineString) linePart).getCoordinates());
            coordList.closeRing();
            break;
        }
        return ring.getFactory().createLinearRing(coordList.toCoordinateArray());
    }

    private boolean cadastralObjectHasTargetBoundary(Polygon coGeom, LineString targetBoundary){
        return coGeom.covers(targetBoundary);
    }
    
    private CadastreBoundarySelectTool getSelectTool() {
        ExtendedAction selectAction =
                this.getMapControl().getMapActionByName(CadastreBoundarySelectTool.NAME);
        return (CadastreBoundarySelectTool) selectAction.getAttachedTool();
    }
}
