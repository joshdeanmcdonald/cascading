/*
 * Copyright (c) 2007-2011 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cascading.flow.planner;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import cascading.flow.FlowException;
import cascading.management.ClientState;
import cascading.stats.StepStats;

/**
 *
 */
public abstract class FlowStepJob implements Callable<Throwable>
  {
  /** Field stepName */
  protected final String stepName;
  /** Field pollingInterval */
  protected long pollingInterval = 1000;
  /** Field predecessors */
  protected List<FlowStepJob> predecessors;
  /** Field latch */
  private final CountDownLatch latch = new CountDownLatch( 1 );
  /** Field wasSuccessful */
  private Boolean wasSuccessful = null;
  /** Field stop */
  private boolean stop = false;
  /** Field flowStep */
  protected final FlowStep flowStep;
  /** Field stepStats */
  protected StepStats stepStats;
  /** Field throwable */
  protected Throwable throwable;

  public FlowStepJob( ClientState clientState, FlowStep flowStep, long pollingInterval )
    {
    this.flowStep = flowStep;
    this.stepName = flowStep.getName();
    this.pollingInterval = pollingInterval;
    this.stepStats = createStepStats( clientState );

    this.stepStats.prepare();
    this.stepStats.markPending();
    }

  protected abstract StepStats createStepStats( ClientState clientState );

  public void stop()
    {
    if( flowStep.isInfoEnabled() )
      flowStep.logInfo( "stopping: " + stepName );

    stop = true;

    // allow pending -> stopped transition
    // never want a hanging pending state
    if( !stepStats.isFinished() )
      stepStats.markStopped();

    try
      {
      internalStop();
      }
    catch( IOException exception )
      {
      flowStep.logWarn( "unable to kill job: " + stepName, exception );
      }
    finally
      {
      stepStats.cleanup();
      }
    }

  protected abstract void internalStop() throws IOException;

  public void setPredecessors( List<FlowStepJob> predecessors )
    {
    this.predecessors = predecessors;
    }

  public Throwable call()
    {
    start();

    return throwable;
    }

  protected void start()
    {
    try
      {
      stepStats.markStarted();

      blockOnPredecessors();

      blockOnJob();
      }
    catch( Throwable throwable )
      {
      dumpDebugInfo();
      this.throwable = throwable;
      }
    finally
      {
      latch.countDown();
      stepStats.cleanup();
      }
    }

  protected void blockOnJob() throws IOException
    {
    if( stop )
      return;

    if( flowStep.isInfoEnabled() )
      flowStep.logInfo( "starting step: " + stepName );

    stepStats.markSubmitted();

    internalNonBlockingStart();

    blockTillCompleteOrStopped();

    if( !stop && !internalNonBlockingIsSuccessful() )
      {
      if( !stepStats.isFinished() )
        stepStats.markFailed( getThrowable() );

      dumpDebugInfo();

      if( !isRemoteExecution() )
        throwable = new FlowException( "local step failed", getThrowable() );
      else
        throwable = new FlowException( "step failed: " + stepName + ", with job id: " + internalJobId() + ", please see cluster logs for failure messages" );
      }
    else
      {
      if( internalNonBlockingIsSuccessful() && !stepStats.isFinished() )
        stepStats.markSuccessful();
      }

    stepStats.captureJobStats();
    }

  protected abstract boolean isRemoteExecution();

  protected abstract String internalJobId();

  protected abstract boolean internalNonBlockingIsSuccessful() throws IOException;

  protected abstract Throwable getThrowable();

  protected abstract void internalNonBlockingStart() throws IOException;

  protected void blockTillCompleteOrStopped() throws IOException
    {
    while( true )
      {
      if( stepStats.isSubmitted() && isStarted() )
        stepStats.markRunning();

      if( stop || internalNonBlockingIsComplete() )
        break;

      sleep();
      }
    }

  protected abstract boolean internalNonBlockingIsComplete() throws IOException;

  protected void sleep()
    {
    try
      {
      Thread.sleep( pollingInterval );
      }
    catch( InterruptedException exception )
      {
      // do nothing
      }
    }

  protected void blockOnPredecessors()
    {
    for( FlowStepJob predecessor : predecessors )
      {
      if( !predecessor.isSuccessful() )
        {
        flowStep.logWarn( "abandoning step: " + stepName + ", predecessor failed: " + predecessor.stepName );

        stop();
        }
      }
    }

  protected abstract void dumpDebugInfo();

  /**
   * Method isSuccessful returns true if this step completed successfully.
   *
   * @return the successful (type boolean) of this FlowStepJob object.
   */
  public boolean isSuccessful()
    {
    try
      {
      latch.await();

      if( wasSuccessful == null )
        wasSuccessful = internalNonBlockingIsSuccessful();
      }
    catch( InterruptedException exception )
      {
      flowStep.logWarn( "latch interrupted", exception );
      }
    catch( IOException exception )
      {
      flowStep.logWarn( "error querying job", exception );
      }
    catch( NullPointerException exception )
      {
      throw new FlowException( "Hadoop is not keeping a large enough job history, please increase the \'mapred.jobtracker.completeuserjobs.maximum\' property", exception );
      }

    if( wasSuccessful != null )
      return wasSuccessful;
    else
      return false;
    }

  /**
   * Method wasStarted returns true if this job was started
   *
   * @return boolean
   */
  public boolean isStarted()
    {
    return internalIsStarted();
    }

  protected abstract boolean internalIsStarted();

  /**
   * Method getStepStats returns the stepStats of this FlowStepJob object.
   *
   * @return the stepStats (type StepStats) of this FlowStepJob object.
   */
  public StepStats getStepStats()
    {
    return stepStats;
    }
  }