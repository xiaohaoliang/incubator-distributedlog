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
package org.apache.distributedlog.service.stream;

import org.apache.distributedlog.AsyncLogWriter;
import org.apache.distributedlog.acl.AccessControlManager;
import org.apache.distributedlog.exceptions.DLException;
import org.apache.distributedlog.exceptions.RequestDeniedException;
import org.apache.distributedlog.service.ResponseUtils;
import org.apache.distributedlog.thrift.service.WriteResponse;
import org.apache.distributedlog.util.Sequencer;
import com.twitter.util.Future;
import org.apache.bookkeeper.feature.Feature;
import org.apache.bookkeeper.stats.Counter;
import org.apache.bookkeeper.stats.StatsLogger;
import scala.runtime.AbstractFunction1;

/**
 * Operation to delete a log stream.
 */
public class DeleteOp extends AbstractWriteOp {
    private final StreamManager streamManager;
    private final Counter deniedDeleteCounter;
    private final AccessControlManager accessControlManager;

    public DeleteOp(String stream,
                    StatsLogger statsLogger,
                    StatsLogger perStreamStatsLogger,
                    StreamManager streamManager,
                    Long checksum,
                    Feature checksumEnabledFeature,
                    AccessControlManager accessControlManager) {
        super(stream, requestStat(statsLogger, "delete"), checksum, checksumEnabledFeature);
        StreamOpStats streamOpStats = new StreamOpStats(statsLogger, perStreamStatsLogger);
        this.deniedDeleteCounter = streamOpStats.requestDeniedCounter("delete");
        this.accessControlManager = accessControlManager;
        this.streamManager = streamManager;
    }

    @Override
    protected Future<WriteResponse> executeOp(AsyncLogWriter writer,
                                              Sequencer sequencer,
                                              Object txnLock) {
        Future<Void> result = streamManager.deleteAndRemoveAsync(streamName());
        return result.map(new AbstractFunction1<Void, WriteResponse>() {
            @Override
            public WriteResponse apply(Void value) {
                return ResponseUtils.writeSuccess();
            }
        });
    }

    @Override
    public void preExecute() throws DLException {
        if (!accessControlManager.allowTruncate(stream)) {
            deniedDeleteCounter.inc();
            throw new RequestDeniedException(stream, "delete");
        }
        super.preExecute();
    }
}
