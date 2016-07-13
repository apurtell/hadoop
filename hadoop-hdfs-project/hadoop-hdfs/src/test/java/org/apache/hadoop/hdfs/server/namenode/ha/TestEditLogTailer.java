/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.namenode.ha;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.ha.ServiceFailedException;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.HAUtil;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.MiniDFSNNTopology;
import org.apache.hadoop.hdfs.server.namenode.FSImage;
import org.apache.hadoop.hdfs.server.namenode.NNStorage;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.hdfs.server.namenode.NameNodeAdapter;
import org.apache.hadoop.test.GenericTestUtils;
import org.apache.log4j.Level;
import org.junit.Test;

import com.google.common.base.Supplier;

public class TestEditLogTailer {
  
  private static final String DIR_PREFIX = "/dir";
  private static final int DIRS_TO_MAKE = 20;
  static final long SLEEP_TIME = 1000;
  static final long NN_LAG_TIMEOUT = 10 * 1000;
  
  static {
    ((Log4JLogger)FSImage.LOG).getLogger().setLevel(Level.ALL);
    ((Log4JLogger)EditLogTailer.LOG).getLogger().setLevel(Level.ALL);
  }
  
  @Test
  public void testTailer() throws IOException, InterruptedException,
      ServiceFailedException {
    Configuration conf = new HdfsConfiguration();
    conf.setInt(DFSConfigKeys.DFS_HA_TAILEDITS_PERIOD_KEY, 1);

    HAUtil.setAllowStandbyReads(conf, true);
    
    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf)
      .nnTopology(MiniDFSNNTopology.simpleHATopology())
      .numDataNodes(0)
      .build();
    cluster.waitActive();
    
    cluster.transitionToActive(0);
    
    NameNode nn1 = cluster.getNameNode(0);
    NameNode nn2 = cluster.getNameNode(1);
    try {
      for (int i = 0; i < DIRS_TO_MAKE / 2; i++) {
        NameNodeAdapter.mkdirs(nn1, getDirPath(i),
            new PermissionStatus("test","test", new FsPermission((short)00755)),
            true);
      }
      
      HATestUtil.waitForStandbyToCatchUp(nn1, nn2);
      
      for (int i = 0; i < DIRS_TO_MAKE / 2; i++) {
        assertTrue(NameNodeAdapter.getFileInfo(nn2,
            getDirPath(i), false).isDir());
      }
      
      for (int i = DIRS_TO_MAKE / 2; i < DIRS_TO_MAKE; i++) {
        NameNodeAdapter.mkdirs(nn1, getDirPath(i),
            new PermissionStatus("test","test", new FsPermission((short)00755)),
            true);
      }
      
      HATestUtil.waitForStandbyToCatchUp(nn1, nn2);
      
      for (int i = DIRS_TO_MAKE / 2; i < DIRS_TO_MAKE; i++) {
        assertTrue(NameNodeAdapter.getFileInfo(nn2,
            getDirPath(i), false).isDir());
      }
    } finally {
      cluster.shutdown();
    }
  }
  
  @Test
  public void testNN0TriggersLogRolls() throws Exception {
    testStandbyTriggersLogRolls(0);
  }
  
  @Test
  public void testNN1TriggersLogRolls() throws Exception {
    testStandbyTriggersLogRolls(1);
  }

  @Test
  public void testNN2TriggersLogRolls() throws Exception {
    testStandbyTriggersLogRolls(2);
  }

  private static void testStandbyTriggersLogRolls(int activeIndex)
      throws Exception {
    Configuration conf = new Configuration();
    // Roll every 1s
    conf.setInt(DFSConfigKeys.DFS_HA_LOGROLL_PERIOD_KEY, 1);
    conf.setInt(DFSConfigKeys.DFS_HA_TAILEDITS_PERIOD_KEY, 1);
    
    // Have to specify IPC ports so the NNs can talk to each other.
    MiniDFSNNTopology topology = new MiniDFSNNTopology()
      .addNameservice(new MiniDFSNNTopology.NSConf("ns1")
        .addNN(new MiniDFSNNTopology.NNConf("nn1").setIpcPort(10031))
        .addNN(new MiniDFSNNTopology.NNConf("nn2").setIpcPort(10032))
        .addNN(new MiniDFSNNTopology.NNConf("nn3").setIpcPort(10033)));

    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf)
      .nnTopology(topology)
      .numDataNodes(0)
      .build();
    try {
      cluster.transitionToActive(activeIndex);
      waitForLogRollInSharedDir(cluster, 3);
    } finally {
      cluster.shutdown();
    }
  }

  /*
    1. when all NN become standby nn, standby NN execute to roll log,
    it will be failed.
    2. when one NN become active, standby NN roll log success.
   */
  @Test
  public void testTriggersLogRollsForAllStandbyNN() throws Exception {
    Configuration conf = new Configuration();

    // Roll every 1s
    conf.setInt(DFSConfigKeys.DFS_HA_LOGROLL_PERIOD_KEY, 1);
    conf.setInt(DFSConfigKeys.DFS_HA_TAILEDITS_PERIOD_KEY, 1);
    conf.setInt(DFSConfigKeys.DFS_HA_TAILEDITS_ALL_NAMESNODES_RETRY_KEY, 100);

    // Have to specify IPC ports so the NNs can talk to each other.
    MiniDFSNNTopology topology = new MiniDFSNNTopology()
        .addNameservice(new MiniDFSNNTopology.NSConf("ns1")
          .addNN(new MiniDFSNNTopology.NNConf("nn1").setIpcPort(10031))
          .addNN(new MiniDFSNNTopology.NNConf("nn2").setIpcPort(10032))
          .addNN(new MiniDFSNNTopology.NNConf("nn3").setIpcPort(10033)));

    MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf)
        .nnTopology(topology)
        .numDataNodes(0)
        .build();
    try {
      cluster.transitionToStandby(0);
      cluster.transitionToStandby(1);
      cluster.transitionToStandby(2);
      try {
        waitForLogRollInSharedDir(cluster, 3);
        fail("After all NN become Standby state, Standby NN should roll log, " +
            "but it will be failed");
      } catch (TimeoutException ignore) {
      }
      cluster.transitionToActive(0);
      waitForLogRollInSharedDir(cluster, 3);
    } finally {
      cluster.shutdown();
    }
  }
  
  private static String getDirPath(int suffix) {
    return DIR_PREFIX + suffix;
  }
  
  private static void waitForLogRollInSharedDir(MiniDFSCluster cluster,
      long startTxId) throws Exception {
    URI sharedUri = cluster.getSharedEditsDir(0, 2);
    File sharedDir = new File(sharedUri.getPath(), "current");
    final File expectedLog = new File(sharedDir,
        NNStorage.getInProgressEditsFileName(startTxId));
    
    GenericTestUtils.waitFor(new Supplier<Boolean>() {
      @Override
      public Boolean get() {
        return expectedLog.exists();
      }
    }, 100, 10000);
  }
}
