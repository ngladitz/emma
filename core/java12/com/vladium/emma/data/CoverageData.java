/* Copyright (C) 2003 Vladimir Roubtsov. All rights reserved.
 * 
 * This program and the accompanying materials are made available under
 * the terms of the Common Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * $Id: CoverageData.java,v 1.1.1.1 2004/05/09 16:57:31 vlad_r Exp $
 */
package com.vladium.emma.data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import com.vladium.util.asserts.$assert;

// ----------------------------------------------------------------------------
/**
 * @author Vlad Roubtsov, (C) 2003
 */
final class CoverageData implements ICoverageData, Cloneable
{
    // public: ................................................................
    
    // TODO: duplicate issue
       
    public Object lock ()
    {
        return m_coverageMap;
    }
    
    public ICoverageData shallowCopy ()
    {
        final CoverageData _clone;
        try
        {
            _clone = (CoverageData) super.clone ();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw new Error (cnse.toString ());
        }
        
        final HashMap _coverageMap;
        
        synchronized (lock ())
        {
            _coverageMap = (HashMap) m_coverageMap.clone ();
        }
        
        _clone.m_coverageMap = _coverageMap;
        
        return _clone;
    }
    
    public int size ()
    {
        return m_coverageMap.size (); 
    }

    public DataHolder getCoverage (final ClassDescriptor cls)
    {
        if (cls == null) throw new IllegalArgumentException ("null input: cls");
        return getCoverage(cls.getClassVMName());
    }

    private DataHolder getCoverage(String classVMName) {
      // Do a merge of all the current coverage, if necessary.      
      List coverages = (List) m_coverageMap.get(classVMName);
      
      if (coverages == null) {
        return null;
      }
      
      if (coverages.size() == 1) {
        return (DataHolder) coverages.get(0);
      }
      
      List mergedCoverageList = new ArrayList();
      DataHolder mergedCoverage = merge(coverages);
      mergedCoverageList.add(mergedCoverage);

      m_coverageMap.put(classVMName, mergedCoverageList);
      return mergedCoverage;
    }
  
    public void addClass (final boolean [][] coverage, final String classVMName, final long stamp)
    {
      addCoverage(classVMName, new DataHolder(coverage, stamp));
    }
    
    private void addCoverage(String classVMName, DataHolder holder) {
      List coverageList = (List) m_coverageMap.get(classVMName);
      if (coverageList == null) {
        coverageList = new ArrayList();
        m_coverageMap.put (classVMName, coverageList);
      }
      // Reset coverage collection if the versions of the classes we're
      // covering don't match. A good idea to issue some warning here.
      if (coverageList.size() > 0) {
        DataHolder firstCoverage = (DataHolder) coverageList.get(0);
        if (firstCoverage.m_stamp != holder.m_stamp) {
          // Might be nice to warn users here.
          coverageList.clear();
        }
      }
      coverageList.add(holder);      
    }
  
    // IMergeable:
    
    public boolean isEmpty ()
    {
        return m_coverageMap.isEmpty ();
    }

    private static DataHolder merge(List coverages) {
      if (coverages.size() == 1) {
        return (DataHolder) coverages.get(0);
      }

      DataHolder mergedCoverage = (DataHolder) coverages.get(0);
      
      for (int i = 1; i < coverages.size(); ++i) {
        merge(mergedCoverage.m_coverage, ((DataHolder)coverages.get(i)).m_coverage);
      }

      return mergedCoverage;
    }
  
    private static void merge(boolean[][] lhs, boolean[][] rhs) {
      for (int i = 0; i < lhs.length; ++i) {
        boolean[] innerLhs = lhs[i];
        boolean[] innerRhs = rhs[i];
        if (innerRhs == null) {
          continue;
        }
        if (innerLhs == null) {
          innerLhs = lhs[i] = new boolean[innerRhs.length];
        }
        for (int j = 0; j < innerLhs.length; ++j) {
          innerLhs[j] |= innerRhs[j];
        }
      }
    }
  
    /*
     * This method is not MT-safe wrt addClass() etc.
     * 
     * note: rhs entries override current entries if they have different stamps;
     * otherwise, the data is merged 
     */    
    public IMergeable merge (final IMergeable rhs)
    {
        if ((rhs == null) || rhs.isEmpty () || (rhs == this))
            return this;
        else
        {
            final CoverageData rhscdata = (CoverageData) rhs; // TODO: redesign so that the cast is not necessary
            final Map rhscoverageData = rhscdata.m_coverageMap;
            
            for (Iterator entries = rhscoverageData.entrySet ().iterator (); entries.hasNext (); )
            {
                final Map.Entry entry = (Map.Entry) entries.next ();
                final String classVMName = (String) entry.getKey ();
                
                final List/*<DataHolder>*/ rhsdatalist = (List/*<DataHolder>*/) entry.getValue ();
                // [assertion: rhsdata != null]

                for (Iterator/*<DataHolder>*/ dataentries = rhsdatalist.iterator(); dataentries.hasNext(); ) {
                    DataHolder rhsdata = (DataHolder) dataentries.next();
                    addCoverage(classVMName, rhsdata);
                }
            }                
            return this;
        }
    }
    
    // protected: .............................................................

    // package: ...............................................................
    
    
    CoverageData ()
    {
        m_coverageMap = new HashMap ();
    }
    
    
    static CoverageData readExternal (final DataInput in)
        throws IOException
    {
        final int size = in.readInt ();
        final HashMap coverageMap = new HashMap (size);
        
        for (int i = 0; i < size; ++ i)
        {
            final String classVMName = in.readUTF ();
            final long stamp = in.readLong ();
            
            final int length = in.readInt ();
            final boolean [][] coverage = new boolean [length][];
            for (int c = 0; c < length; ++ c) 
            {
                coverage [c] = DataFactory.readBooleanArray (in);
            }
            
            List coverages = new ArrayList(1);
            coverages.add(new DataHolder(coverage, stamp));
            coverageMap.put (classVMName, coverages);
        }
        
        return new CoverageData (coverageMap);
    }
    
    static void writeExternal (final CoverageData cdata, final DataOutput out)
        throws IOException
    {
        final Map coverageMap = cdata.m_coverageMap;
        
        final int size = coverageMap.size ();
        out.writeInt (size);
        
        final Iterator entries = coverageMap.keySet().iterator ();
        for (int i = 0; i < size; ++ i)
        {            
            final String classVMName = (String) entries.next();
            final DataHolder data = cdata.getCoverage(classVMName);
            
            final boolean [][] coverage = data.m_coverage;
            
            out.writeUTF (classVMName);
            out.writeLong (data.m_stamp);
            
            final int length = coverage.length;
            out.writeInt (length);
            for (int c = 0; c < length; ++ c)
            {
                DataFactory.writeBooleanArray (coverage [c], out);
            }
        }
    }

    // private: ...............................................................
    
    
    private CoverageData (final HashMap coverageMap)
    {
        if ($assert.ENABLED) $assert.ASSERT (coverageMap != null, "coverageMap is null");
        m_coverageMap = coverageMap;
    }
    
    
    /**
     * A Map of JVM class names to List<DataHolder>
     * 
     * All DataHolders for a given class name must have the same stamp.
     * In the case where we encounter two different versions of the same named
     * class (different implementations), we throw away the existing
     * coverage information and start fresh. This is not because it's the 
     * "right thing" to do, but because it's the most compatible with Emma's
     * prior behavior. 
     */
    private /*final*/ HashMap/*<String, List<DataHolder>>*/ m_coverageMap; // never null

} // end of class
// ----------------------------------------------------------------------------