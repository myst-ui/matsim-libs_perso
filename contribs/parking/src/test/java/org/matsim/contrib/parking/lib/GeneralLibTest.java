package org.matsim.contrib.parking.lib;

import static org.junit.Assert.*;

import org.matsim.contrib.parking.parkingchoice.lib.GeneralLib;
import org.matsim.contrib.parking.parkingchoice.lib.obj.Matrix;
import org.matsim.testcases.MatsimTestCase;


public class GeneralLibTest extends MatsimTestCase {


//	@org.junit.Test public void testReadWritePopulation(){
//		Scenario scenario = GeneralLib.readScenario("test/scenarios/equil/plans2.xml","test/scenarios/equil/network.xml" );
//
//		GeneralLib.writePopulation(scenario.getPopulation(), scenario.getNetwork(), getOutputDirectory() + "plans2.xml");
//
//		scenario = GeneralLib.readScenario(getOutputDirectory() + "plans2.xml","test/scenarios/equil/network.xml" );
//
//		assertEquals(2, scenario.getPopulation().getPersons().size());
//	}
//
//	@org.junit.Test public void testReadNetwork(){
//		Network network = GeneralLib.readNetwork("test/scenarios/equil/network.xml");
//		assertEquals(23, network.getLinks().size());
//	}
//
//	@org.junit.Test public void testReadWriteFacilities(){
//		ActivityFacilities facilities=GeneralLib.readActivityFacilities("test/scenarios/equil/facilities.xml");
//
//		GeneralLib.writeActivityFacilities(facilities, getOutputDirectory() + "facilities.xml");
//
//		facilities=GeneralLib.readActivityFacilities(getOutputDirectory() + "facilities.xml");
//
//		assertEquals(23, facilities.getFacilities().size());
//
//	}

	@org.junit.Test public void testReadWriteMatrix(){
		double[][] hubPriceInfoOriginal=GeneralLib.readMatrix(96, 4, false, getClassInputDirectory() +  "tabTable.txt");

		GeneralLib.writeMatrix(hubPriceInfoOriginal, getOutputDirectory() + "hubPriceInfo.txt", null);

		double[][] hubPriceInfoRead=GeneralLib.readMatrix(96, 4, false, getOutputDirectory() + "hubPriceInfo.txt");

		assertTrue(isEqual(hubPriceInfoOriginal, hubPriceInfoRead));
	}

	private boolean isEqual(double[][] matrixA, double[][] matrixB){
		for (int i=0;i<matrixA.length;i++){
			for (int j=0;j<matrixA[0].length;j++){
				if (matrixA[i][j]!=matrixB[i][j]){
					return false;
				}
			}
		}

		return true;
	}

	@org.junit.Test public void testScaleMatrix(){
		double[][] matrix=new double[1][1];
		matrix[0][0]=1.0;
		matrix=GeneralLib.scaleMatrix(matrix, 2);

		assertEquals(2.0, matrix[0][0], 0);
	}


	@org.junit.Test public void testProjectTimeWithin24Hours(){
		assertEquals(10.0,GeneralLib.projectTimeWithin24Hours(10.0), 0);
		assertEquals(0.0,GeneralLib.projectTimeWithin24Hours(60*60*24.0), 0);
		assertEquals(1.0,GeneralLib.projectTimeWithin24Hours(60*60*24.0+1),0.1);
		assertEquals(60*60*24.0-1,GeneralLib.projectTimeWithin24Hours(-1),0.1);
	}

	@org.junit.Test public void testGetIntervalDuration(){
		assertEquals(10.0,GeneralLib.getIntervalDuration(0.0,10.0), 0);
		assertEquals(11.0,GeneralLib.getIntervalDuration(60*60*24.0-1.0,10.0), 0);
	}


	@org.junit.Test public void testIsIn24HourInterval(){
		assertEquals(true,GeneralLib.isIn24HourInterval(0.0, 10.0, 9.0));
		assertEquals(false,GeneralLib.isIn24HourInterval(0.0, 10.0, 11.0));
		assertEquals(true,GeneralLib.isIn24HourInterval(0.0, 10.0, 0.0));
		assertEquals(true,GeneralLib.isIn24HourInterval(0.0, 10.0, 10.0));

		assertEquals(false,GeneralLib.isIn24HourInterval(10.0, 3.0, 9.0));
		assertEquals(true,GeneralLib.isIn24HourInterval(10.0, 3.0, 11.0));
		assertEquals(true,GeneralLib.isIn24HourInterval(10.0, 3.0, 2.0));
	}

	@org.junit.Test public void testReadStringMatrix(){
		System.out.println();
		Matrix matrix=GeneralLib.readStringMatrix(getClassInputDirectory() +  "tabTable.txt");

		assertEquals(96, matrix.getNumberOfRows());
		assertEquals(4, matrix.getNumberOfColumnsInRow(0));
		assertEquals(80.0, matrix.getDouble(0, 0),0.1);
	}

	@org.junit.Test public void testIsNumberInBetween(){
		assertTrue(GeneralLib.isNumberInBetween(5.0, 7.0, 6.0));
		assertTrue(GeneralLib.isNumberInBetween(7.0, 5.0, 6.0));
		assertFalse(GeneralLib.isNumberInBetween(6.0, 6.0, 7.0));
		assertFalse(GeneralLib.isNumberInBetween(6.0, 6.0, 6.0));
	}
}
