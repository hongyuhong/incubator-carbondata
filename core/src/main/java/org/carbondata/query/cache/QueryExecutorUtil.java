/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.carbondata.query.cache;

import java.util.*;

import org.carbondata.common.logging.LogService;
import org.carbondata.common.logging.LogServiceFactory;
import org.carbondata.core.carbon.SqlStatement;
import org.carbondata.core.keygenerator.KeyGenException;
import org.carbondata.core.keygenerator.KeyGenerator;
import org.carbondata.core.metadata.CarbonMetadata.Dimension;
import org.carbondata.core.vo.HybridStoreModel;
import org.carbondata.query.datastorage.InMemoryTable;
import org.carbondata.query.util.CarbonEngineLogEvent;

/**
 * Util class
 */
public final class QueryExecutorUtil {

    /**
     * LOGGER
     */
    private static final LogService LOGGER =
            LogServiceFactory.getLogService(QueryExecutorUtil.class.getName());

    private QueryExecutorUtil() {

    }

    /**
     * Get surrogates
     *
     * @param slices
     * @param columnName
     * @param name
     * @return
     */
    public static void getMemberIdByName(List<InMemoryTable> slices, Dimension columnName,
            String name, List<Long> surrogatesActual) {
        List<Long> surrogates = new ArrayList<Long>(10);
        if (null != slices && null != columnName)//Coverity Fix add
        {
            long surr = 0;
            boolean hasNameColumn = columnName.isHasNameColumn() && !columnName.isActualCol();
            for (InMemoryTable slice : slices) {

                long surrLoc = (long) slice.getMemberCache(
                        columnName.getTableName() + '_' + columnName.getColName() + '_' + columnName
                                .getDimName() + '_' + columnName.getHierName())
                        .getMemberId(name, columnName.isActualCol());
                if (surrLoc > 0) {
                    surr = surrLoc;
                }

            }//CHECKSTYLE:ON

            if (hasNameColumn && surrogates.size() == 0) {
                surrogates.add(Long.MAX_VALUE);
                //LOGGER.error(MolapEngineLogEvent.U
                LOGGER.error(CarbonEngineLogEvent.UNIBI_MOLAPENGINE_MSG,
                        " Member does not exist for name column :" + name);
            }
            if (surr > 0) {
                surrogates.add(surr);
            } else if (!hasNameColumn) {
                LOGGER.error(CarbonEngineLogEvent.UNIBI_MOLAPENGINE_MSG,
                        " Member does not exist for level " + columnName.getName() + " : " + name);
                surrogates.add(Long.MAX_VALUE);
            }
        }
        surrogatesActual.addAll(surrogates);
    }

    /**
     * To get the max key based on dimensions. i.e. all other dimensions will be
     * set to 0 bits and the required query dimension will be masked with all
     * 1's so that we can mask key and then compare while aggregating
     *
     * @param queryDimensions
     * @return
     * @throws KeyGenException
     */
    public static byte[] getMaxKeyBasedOnDimensions(Dimension[] queryDimensions,
            KeyGenerator generator, Dimension[] dimTables) throws KeyGenException {
        long[] max = new long[dimTables.length];
        Arrays.fill(max, 0L);
        for (int i = 0; i < queryDimensions.length; i++) {
            if (queryDimensions[i].isHighCardinalityDim()) {
                continue;
            }
            max[queryDimensions[i].getOrdinal()] = Long.MAX_VALUE;
        }
        return generator.generateKey(max);
    }

    /**
     * getDimension
     *
     * @param dimGet
     * @param dims
     * @return
     */
    public static Dimension getDimension(Dimension dimGet, Dimension[] dims) {
        for (Dimension dimension : dims) {
            if (dimGet.getDimName().equals(dimension.getDimName()) && dimGet.getHierName()
                    .equals(dimension.getHierName()) && dimGet.getName()
                    .equals(dimension.getName())) {
                return dimension;
            }
        }
        return dimGet;
    }

    /**
     * It checks whether the passed dimension is there in list or not.
     *
     * @param dimension
     * @param dimList
     * @return
     */
    public static boolean contain(Dimension dimension, List<Dimension> dimList) {
        for (Dimension dim : dimList) {
            if (dim.getHierName().equals(dimension.getHierName()) && dim.getDimName()
                    .equals(dimension.getDimName()) && dim.getName().equals(dimension.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * It checks whether the passed dimension is there in list or not.
     *
     * @param dimension
     * @param dimList
     * @return
     */
    public static boolean contain(Dimension dimension, Dimension[] dimList) {
        for (Dimension dim : dimList) {
            if (dim.getHierName().equals(dimension.getHierName()) && dim.getDimName()
                    .equals(dimension.getDimName()) && dim.getName().equals(dimension.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param generator
     * @param maskedKeyRanges
     * @param ranges
     * @param byteIndexs
     * @param key
     * @return
     * @throws KeyGenException
     */
    private static byte[] getMaskedKey(KeyGenerator generator, int[] maskedKeyRanges,
            List<Integer> ranges, int[] byteIndexs, long[] key) throws KeyGenException {
        byte[] mdKey = generator.generateKey(key);

        byte[] maskedKey = new byte[byteIndexs.length];

        //For better performance.
        //         System.arraycopy(mdKey, 0, maskedKey, 0, byteIndexs.length);
        //CHECKSTYLE:OFF    Approval No:Approval-284
        for (int i = 0; i < byteIndexs.length; i++) {
            maskedKey[i] = mdKey[byteIndexs[i]];
        }
        //CHECKSTYLE:ON
        for (int i = 0; i < byteIndexs.length; i++) {
            for (int k = 0; k < maskedKeyRanges.length; k++) {
                if (byteIndexs[i] == maskedKeyRanges[k]) {
                    ranges.add(k);
                    break;
                }
            }
        }

        return maskedKey;
    }

    /**
     * getMaskedBytesForRollUp.
     *
     * @param dims
     * @param generator
     * @param maskedKeyRanges
     * @param ranges
     * @return
     * @throws KeyGenException
     */
    public static byte[] getMaskedBytesForRollUp(int[] dims, KeyGenerator generator,
            int[] maskedKeyRanges, List<Integer> ranges) throws KeyGenException {
        Set<Integer> integers = new TreeSet<Integer>();

        //
        for (int i = 0; i < dims.length; i++) {

            int[] range = generator.getKeyByteOffsets(dims[i]);
            for (int j = range[0]; j <= range[1]; j++) {
                integers.add(j);
            }
        }
        //
        int[] byteIndexs = new int[integers.size()];
        int j = 0;
        for (Iterator<Integer> iterator = integers.iterator(); iterator.hasNext(); ) {
            Integer integer = (Integer) iterator.next();
            byteIndexs[j++] = integer.intValue();
        }

        long[] key = new long[generator.getDimCount()];
        for (int i = 0; i < dims.length; i++) {
            key[dims[i]] = Long.MAX_VALUE;
        }

        return getMaskedKey(generator, maskedKeyRanges, ranges, byteIndexs, key);
    }

    /**
     * This method will return the ranges for the masked Bytes
     * based on the key Generator.
     *
     * @param queryDimensions
     * @param generator
     * @return
     */
    public static int[] getRangesForMaskedByte(int[] queryDimensions, KeyGenerator generator) {

        return getRanges(queryDimensions, generator);
    }

    //TODO SIMIAN

    /**
     * @param queryDimensions
     * @param generator
     * @return
     */
    private static int[] getRanges(int[] queryDimensions, KeyGenerator generator) {
        Set<Integer> integers = new TreeSet<Integer>();
        //
        for (int i = 0; i < queryDimensions.length; i++) {

            int[] range = generator.getKeyByteOffsets(queryDimensions[i]);
            for (int j = range[0]; j <= range[1]; j++) {
                integers.add(j);
            }

        }
        //
        int[] byteIndexs = new int[integers.size()];
        int j = 0;
        for (Iterator<Integer> iterator = integers.iterator(); iterator.hasNext(); ) {
            Integer integer = (Integer) iterator.next();
            byteIndexs[j++] = integer.intValue();
        }

        return byteIndexs;
    }

    /**
     * Converts int list to int[]
     *
     * @param integers
     * @return
     */
    public static int[] convertIntegerListToIntArray(Collection<Integer> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++) {//CHECKSTYLE:OFF    Approval No:Approval-284
            ret[i] = iterator.next().intValue();
        }//CHECKSTYLE:ON
        return ret;
    }

    /**
     * getMaskedByte
     *
     * @param queryDimensions
     * @param generator
     * @return
     */
    public static int[] getMaskedByte(Dimension[] queryDimensions, KeyGenerator generator,
            HybridStoreModel hm) {

        Set<Integer> integers = new TreeSet<Integer>();
        boolean isRowAdded = false;

        for (int i = 0; i < queryDimensions.length; i++) {

            if (queryDimensions[i].isHighCardinalityDim()) {
                continue;
            } else if (queryDimensions[i].getDataType() == SqlStatement.Type.ARRAY) {
                continue;
            } else if (queryDimensions[i].getDataType() == SqlStatement.Type.STRUCT) continue;
            else if (queryDimensions[i].getParentName() != null) continue;
                //if querydimension is row store based, than add all row store ordinal in mask, because row store ordinal rangesare overalapped
                //for e.g its possible
                //dimension1 range: 0-1
                //dimension2 range: 1-2
                //hence to read only dimension2, you have to mask dimension1 also
            else if (!queryDimensions[i].isColumnar()) {
                //if all row store ordinal is already added in range than no need to consider it again
                if (!isRowAdded) {
                    isRowAdded = true;
                    int[] rowOrdinals = hm.getRowStoreOrdinals();
                    for (int r = 0; r < rowOrdinals.length; r++) {
                        int[] range =
                                generator.getKeyByteOffsets(hm.getMdKeyOrdinal(rowOrdinals[r]));
                        for (int j = range[0]; j <= range[1]; j++) {
                            integers.add(j);
                        }

                    }
                }
                continue;

            } else {
                int[] range = generator
                        .getKeyByteOffsets(hm.getMdKeyOrdinal(queryDimensions[i].getOrdinal()));
                for (int j = range[0]; j <= range[1]; j++) {
                    integers.add(j);
                }
            }

        }
        //
        int[] byteIndexs = new int[integers.size()];
        int j = 0;
        for (Iterator<Integer> iterator = integers.iterator(); iterator.hasNext(); ) {
            Integer integer = (Integer) iterator.next();
            byteIndexs[j++] = integer.intValue();
        }

        return byteIndexs;
    }

    /**
     * updateMaskedKeyRanges
     *
     * @param maskedKey
     * @param maskedKeyRanges
     */
    public static void updateMaskedKeyRanges(int[] maskedKey, int[] maskedKeyRanges) {
        Arrays.fill(maskedKey, -1);
        for (int i = 0; i < maskedKeyRanges.length; i++) {
            maskedKey[maskedKeyRanges[i]] = i;
        }
    }

}
