/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    InfoGainAttributeEval.java
 *    Copyright (C) 1999 Mark Hall
 *
 */

package  weka.attributeSelection;

import  java.io.*;
import  java.util.*;
import  weka.core.*;
import  weka.filters.*;

/** 
 * Class for Evaluating attributes individually by measuring information gain 
 * with respect to the class.
 *
 * Valid options are:<p>
 *
 * -M <br>
 * Treat missing values as a seperate value. <br>
 *
 * -B <br>
 * Just binarize numeric attributes instead of properly discretizing them. <br>
 *
 * @author Mark Hall (mhall@cs.waikato.ac.nz)
 * @version $Revision: 1.12 $
 */
public class InfoGainAttributeEval
  extends AttributeEvaluator
  implements OptionHandler {

  /** Treat missing values as a seperate value */
  private boolean m_missing_merge;

  /** Just binarize numeric attributes */
  private boolean m_Binarize;

  /** The info gain for each attribute */
  private double[] m_InfoGains;

  /**
   * Returns a string describing this attribute evaluator
   * @return a description of the evaluator suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return "InfoGainAttributeEval :\n\nEvaluates the worth of an attribute "
      +"by measuring the information gain with respect to the class.\n\n"
      +"InfoGain(Class,Attribute) = H(Class) - H(Class | Attribute).\n";
  }

  /**
   * Constructor
   */
  public InfoGainAttributeEval () {
    resetOptions();
  }

  /**
   * Returns an enumeration describing the available options.
   * @return an enumeration of all the available options.
   **/
  public Enumeration listOptions () {
    Vector newVector = new Vector(2);
    newVector.addElement(new Option("\ttreat missing values as a seperate " 
				    + "value.", "M", 0, "-M"));
    newVector.addElement(new Option("\tjust binarize numeric attributes instead\n " 
				    +"\tof properly discretizing them.", "B", 0, 
				    "-B"));
    return  newVector.elements();
  }


  /**
   * Parses a given list of options. <p>
   *
   * Valid options are:<p>
   *
   * -M <br>
   * Treat missing values as a seperate value. <br>
   *
   * -B <br>
   * Just binarize numeric attributes instead of properly discretizing them. <br>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   *
   **/
  public void setOptions (String[] options)
    throws Exception {

    resetOptions();
    setMissingMerge(!(Utils.getFlag('M', options)));
    setBinarizeNumericAttributes(Utils.getFlag('B', options));
  }


  /**
   * Gets the current settings of WrapperSubsetEval.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String[] getOptions () {
    String[] options = new String[2];
    int current = 0;

    if (!getMissingMerge()) {
      options[current++] = "-M";
    }
    if (getBinarizeNumericAttributes()) {
      options[current++] = "-B";
    }

    while (current < options.length) {
      options[current++] = "";
    }

    return  options;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String binarizeNumericAttributesTipText() {
    return "Just binarize numeric attributes instead of properly discretizing them.";
  }

  /**
   * Binarize numeric attributes.
   *
   * @param b true=binarize numeric attributes
   */
  public void setBinarizeNumericAttributes (boolean b) {
    m_Binarize = b;
  }


  /**
   * get whether numeric attributes are just being binarized.
   *
   * @return true if missing values are being distributed.
   */
  public boolean getBinarizeNumericAttributes () {
    return  m_Binarize;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String missingMergeTipText() {
    return "Distribute counts for missing values. Counts are distributed "
      +"across other values in proportion to their frequency. Otherwise, "
      +"missing is treated as a separate value.";
  }

  /**
   * distribute the counts for missing values across observed values
   *
   * @param b true=distribute missing values.
   */
  public void setMissingMerge (boolean b) {
    m_missing_merge = b;
  }


  /**
   * get whether missing values are being distributed or not
   *
   * @return true if missing values are being distributed.
   */
  public boolean getMissingMerge () {
    return  m_missing_merge;
  }


  /**
   * Initializes an information gain attribute evaluator.
   * Discretizes all attributes that are numeric.
   *
   * @param data set of instances serving as training data 
   * @exception Exception if the evaluator has not been 
   * generated successfully
   */
  public void buildEvaluator (Instances data)
    throws Exception {
    
    if (data.checkForStringAttributes()) {
      throw  new Exception("Can't handle string attributes!");
    }
    
    int classIndex = data.classIndex();
    if (data.attribute(classIndex).isNumeric()) {
      throw  new Exception("Class must be nominal!");
    }
    int numInstances = data.numInstances();
    
    if (!m_Binarize) {
      DiscretizeFilter disTransform = new DiscretizeFilter();
      disTransform.setUseBetterEncoding(true);
      disTransform.setInputFormat(data);
      data = Filter.useFilter(data, disTransform);
    } else {
      NumericToBinaryFilter binTransform = new NumericToBinaryFilter();
      binTransform.setInputFormat(data);
      data = Filter.useFilter(data, binTransform);
    }      
    int numClasses = data.attribute(classIndex).numValues();

    // Reserve space and initialize counters
    double[][][] counts = new double[data.numAttributes()][][];
    for (int k = 0; k < data.numAttributes(); k++) {
      if (k != classIndex) {
	int numValues = data.attribute(k).numValues();
	counts[k] = new double[numValues + 1][numClasses + 1];
      }
    }

    // Initialize counters
    double[] temp = new double[numClasses + 1];
    for (int k = 0; k < numInstances; k++) {
      Instance inst = data.instance(k);
      if (inst.classIsMissing()) {
	temp[numClasses] += inst.weight();
      } else {
	temp[(int)inst.classValue()] += inst.weight();
      }
    }
    for (int k = 0; k < counts.length; k++) {
      if (k != classIndex) {
	for (int i = 0; i < temp.length; i++) {
	  counts[k][0][i] = temp[i];
	}
      }
    }

    // Get counts
    for (int k = 0; k < numInstances; k++) {
      Instance inst = data.instance(k);
      for (int i = 0; i < inst.numValues(); i++) {
	if (inst.index(i) != classIndex) {
	  if (inst.isMissingSparse(i) || inst.classIsMissing()) {
	    if (!inst.isMissingSparse(i)) {
	      counts[inst.index(i)][(int)inst.valueSparse(i)][numClasses] += 
		inst.weight();
	      counts[inst.index(i)][0][numClasses] -= inst.weight();
	    } else if (!inst.classIsMissing()) {
	      counts[inst.index(i)][data.attribute(inst.index(i)).numValues()]
		[(int)inst.classValue()] += inst.weight();
	      counts[inst.index(i)][0][(int)inst.classValue()] -= 
		inst.weight();
	    } else {
	      counts[inst.index(i)][data.attribute(inst.index(i)).numValues()]
		[numClasses] += inst.weight();
	      counts[inst.index(i)][0][numClasses] -= inst.weight();
	    }
	  } else {
	    counts[inst.index(i)][(int)inst.valueSparse(i)]
	      [(int)inst.classValue()] += inst.weight();
	    counts[inst.index(i)][0][(int)inst.classValue()] -= inst.weight();
	  }
	}
      }
    }

    // distribute missing counts if required
    if (m_missing_merge) {
      
      for (int k = 0; k < data.numAttributes(); k++) {
	if (k != classIndex) {
	  int numValues = data.attribute(k).numValues();

	  // Compute marginals
	  double[] rowSums = new double[numValues];
	  double[] columnSums = new double[numClasses];
	  double sum = 0;
	  for (int i = 0; i < numValues; i++) {
	    for (int j = 0; j < numClasses; j++) {
	      rowSums[i] += counts[k][i][j];
	      columnSums[j] += counts[k][i][j];
	    }
	    sum += rowSums[i];
	  }

	  if (Utils.gr(sum, 0)) {
	    double[][] additions = new double[numValues][numClasses];

	    // Compute what needs to be added to each row
	    for (int i = 0; i < numValues; i++) {
	      for (int j = 0; j  < numClasses; j++) {
		additions[i][j] = (rowSums[i] / sum) * counts[k][numValues][j];
	      }
	    }
	    
	    // Compute what needs to be added to each column
	    for (int i = 0; i < numClasses; i++) {
	      for (int j = 0; j  < numValues; j++) {
		additions[j][i] += (columnSums[i] / sum) * 
		  counts[k][j][numClasses];
	      }
	    }
	    
	    // Compute what needs to be added to each cell
	    for (int i = 0; i < numClasses; i++) {
	      for (int j = 0; j  < numValues; j++) {
		additions[j][i] += (counts[k][j][i] / sum) * 
		  counts[k][numValues][numClasses];
	      }
	    }
	    
	    // Make new contingency table
	    double[][] newTable = new double[numValues][numClasses];
	    for (int i = 0; i < numValues; i++) {
	      for (int j = 0; j < numClasses; j++) {
		newTable[i][j] = counts[k][i][j] + additions[i][j];
	      }
	    }
	    counts[k] = newTable;
	  }
	}
      }
    }

    // Compute info gains
    m_InfoGains = new double[data.numAttributes()];
    for (int i = 0; i < data.numAttributes(); i++) {
      if (i != classIndex) {
	m_InfoGains[i] = 
	  (ContingencyTables.entropyOverColumns(counts[i]) 
	   - ContingencyTables.entropyConditionedOnRows(counts[i]));
      }
    }
  }

  /**
   * Reset options to their default values
   */
  protected void resetOptions () {
    m_InfoGains = null;
    m_missing_merge = true;
    m_Binarize = false;
  }


  /**
   * evaluates an individual attribute by measuring the amount
   * of information gained about the class given the attribute.
   *
   * @param attribute the index of the attribute to be evaluated
   * @exception Exception if the attribute could not be evaluated
   */
  public double evaluateAttribute (int attribute)
    throws Exception {

    return m_InfoGains[attribute];
  }

  /**
   * Describe the attribute evaluator
   * @return a description of the attribute evaluator as a string
   */
  public String toString () {
    StringBuffer text = new StringBuffer();

    if (m_InfoGains == null) {
      text.append("Information Gain attribute evaluator has not been built");
    }
    else {
      text.append("\tInformation Gain Ranking Filter");
      if (!m_missing_merge) {
	text.append("\n\tMissing values treated as seperate");
      }
      if (m_Binarize) {
	text.append("\n\tNumeric attributes are just binarized");
      }
    }
    
    text.append("\n");
    return  text.toString();
  }

  
  // ============
  // Test method.
  // ============
  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main (String[] args) {
    try {
      System.out.println(AttributeSelection.
			 SelectAttributes(new InfoGainAttributeEval(), args));
    }
    catch (Exception e) {
      e.printStackTrace();
      System.out.println(e.getMessage());
    }
  }
}


