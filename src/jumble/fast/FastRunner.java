package jumble.fast;

import com.reeltwo.util.Debug;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import jumble.mutation.Mutater;
import jumble.mutation.Mutation;
import jumble.util.IOThread;
import jumble.util.JavaRunner;
import junit.framework.TestResult;

/**
 * A runner for the <CODE>FastJumbler</CODE>. Runs the FastJumbler in a new
 * JVM and detects timeouts.
 * 
 * @author Tin Pavlinic
 * @version $Revision$
 */
public class FastRunner {

  /** Filename for the cache */
  public static final String CACHE_FILENAME = "jumble-cache.dat";

  // Configuration properties

  /** Whether to mutate constants */
  private boolean mInlineConstants = true;

  /** Whether to mutate return values */
  private boolean mReturnVals = true;

  /** Whether to mutate increments */
  private boolean mIncrements = true;

  private boolean mOrdered = true;

  private boolean mVerbose = false;

  private boolean mLoadCache = true;
  private boolean mSaveCache = true;
  private boolean mUseCache = true;

  private Set mExcludeMethods = new HashSet();

  // State during run

  /** The class being tested */
  private String mClassName;
  private String mCacheFileName;
  private String mTestSuiteFileName;
  private JavaRunner mRunner = new JavaRunner("jumble.fast.FastJumbler");
  private Process mChildProcess = null;
  private IOThread mIot = null;
  private IOThread mEot = null;
  private int mMutationCount;
  private long mTotalRuntime;

  /** The variable storing the failed tests - can get pretty big */
  FailedTestMap mCache = null;


  /**
   * Gets whether verbose mode is set.
   *
   * @return true if verbose mode is enabled.
   */
  public boolean isVerbose() {
    return mVerbose;
  }

  /**
   * Sets whether verbose mode is enabled.
   *
   * @param newVerbose true if verbose mode should be enabled.
   */
  public void setVerbose(final boolean newVerbose) {
    mVerbose = newVerbose;
  }

  
  /**
   * Gets whether inline constants will be mutated.
   *
   * @return true if inline constants will be mutated.
   */
  public boolean isInlineConstants() {
    return mInlineConstants;
  }

  /**
   * Sets whether inline constants will be mutated.
   *
   * @param argInlineConstants true if inline constants should be mutated.
   */
  public void setInlineConstants(final boolean argInlineConstants) {
    mInlineConstants = argInlineConstants;
  }

  /**
   * Gets whether return values will be mutated.
   *
   * @return true if return values will be mutated.
   */
  public boolean isReturnVals() {
    return mReturnVals;
  }

  /**
   * Sets whether return values will be mutated.
   *
   * @param argReturnVals true return values should be mutated.
   */
  public void setReturnVals(final boolean argReturnVals) {
    mReturnVals = argReturnVals;
  }

  /**
   * Gets whether increments will be mutated.
   *
   * @return true if increments will be mutated.
   */
  public boolean isIncrements() {
    return mIncrements;
  }

  /**
   * Sets whether increments will be mutated.
   *
   * @param argIncrements true if increments will be mutated.
   */
  public void setIncrements(final boolean argIncrements) {
    mIncrements = argIncrements;
  }



  /**
   * Gets whether tests are ordered by their run time.
   *
   * @return true if tests are ordered by their run time.
   */
  public boolean isOrdered() {
    return mOrdered;
  }

  /**
   * Sets whether tests are ordered by their run time.
   *
   * @param argOrdered true if tests should be ordered by their run time.
   */
  public void setOrdered(final boolean argOrdered) {
    mOrdered = argOrdered;
  }

  /**
   * Gets the value of loadCache
   *
   * @return the value of loadCache
   */
  public boolean isLoadCache() {
    return mLoadCache;
  }

  /**
   * Sets the value of loadCache
   *
   * @param argLoadCache Value to assign to loadCache
   */
  public void setLoadCache(final boolean argLoadCache) {
    mLoadCache = argLoadCache;
  }

  /**
   * Gets the value of saveCache
   *
   * @return the value of saveCache
   */
  public boolean isSaveCache() {
    return mSaveCache;
  }

  /**
   * Sets the value of saveCache
   *
   * @param argSaveCache Value to assign to saveCache
   */
  public void setSaveCache(final boolean argSaveCache) {
    mSaveCache = argSaveCache;
  }

  /**
   * Gets the value of useCache
   *
   * @return the value of useCache
   */
  public boolean isUseCache() {
    return mUseCache;
  }

  /**
   * Sets the value of useCache
   *
   * @param argUseCache Value to assign to useCache
   */
  public void setUseCache(final boolean argUseCache) {
    mUseCache = argUseCache;
  }

  /**
   * Gets the set of excluded method names
   *
   * @return the set of excluded method names
   */
  public Set getExcludeMethods() {
    return mExcludeMethods;
  }


  /**
   * A function which computes the timeout for given that the original test took
   * <CODE>runtime</CODE>
   * 
   * @param runtime
   *          the original runtime
   * @return the computed timeout
   */
  public static long computeTimeout(long runtime) {
    return runtime * 10 + 2000;
  }

  private void initCache() throws Exception {
    boolean loaded = false;
      
    // Load the cache if it exists and is needed
    if (mLoadCache) {
      try {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILENAME));
        mCache = (FailedTestMap) ois.readObject();
        loaded = true;
      } catch (IOException e) {
        loaded = false;
      }
    }
    if (!loaded) {
      mCache = new FailedTestMap();
    }
  }

  private void updateCache(Mutation mutation) {
    if (mutation.isPassed()) {
      // Remove "PASS: " and tokenize
      StringTokenizer tokens = new StringTokenizer(mutation.getDescription().substring(6), ":");
      String clazzName = tokens.nextToken();
      assert clazzName.equals(mutation.getClassName());
      String methodName = tokens.nextToken();
      int mutPoint = Integer.parseInt(tokens.nextToken());
      String testName = tokens.nextToken();
      mCache.addFailure(clazzName, methodName, mutPoint, testName);
    }
  }

  private boolean writeCache(String cacheFileName) {
    try {
      File f = new File(cacheFileName);
      if (f.exists()) {
        f.delete();
      }
      ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(cacheFileName));
      o.writeObject(mCache);
      o.close();
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  public void addExcludeMethod(String methodName) {
    mExcludeMethods.add(methodName);
  }

  /** Constructs arguments to the FastJumbler */
  private String[] createArgs(int currentMutation) {
    ArrayList args = new ArrayList();
    // mutation point
    args.add("-s");
    args.add(String.valueOf(currentMutation));
    // class name
    args.add(mClassName);
    // test suite filename
    args.add(mTestSuiteFileName);
    
    if (mUseCache) {
      // Write a temp cache
      if (writeCache(mCacheFileName)) {
        args.add(mCacheFileName);
      }
    }
    
    // exclude methods
    if (!mExcludeMethods.isEmpty()) {
      StringBuffer ex = new StringBuffer();
      Iterator it = mExcludeMethods.iterator();
      for (int i = 0; i < mExcludeMethods.size(); i++) {
        if (i == 0) {
          ex.append(it.next());
        } else {
          ex.append("," + it.next());
        }
      }
      args.add("-x");
      args.add(ex.toString());
    }
    // inline constants
    if (mInlineConstants) {
      args.add("-k");
    }
    // return values
    if (mReturnVals) {
      args.add("-r");
    }
    // increments
    if (mIncrements) {
      args.add("-i");
    }
    // verbose
    if (mVerbose) {
      args.add("-v");
    }
    return (String[]) args.toArray(new String[args.size()]);
  }


  private Mutater createMutater(int mutationpoint) {
    // Get the number of mutation points from the Jumbler
    final Mutater m = new Mutater(mutationpoint);
    m.setIgnoredMethods(mExcludeMethods);
    m.setMutateIncrements(mIncrements);
    m.setMutateInlineConstants(mInlineConstants);
    m.setMutateReturnValues(mReturnVals);
    return m;
  }

  private int countMutationPoints() {
    return createMutater(0).countMutationPoints(mClassName);
  }

  private boolean debugOutput(String out, String err) {
    if (err != null) {
      System.err.println("Child.err->" + err); 
    }
    if (out != null) {
      System.err.println("Child.out->" + out);
    }
    return true; // So we can be enabled/disabled via assertion mechanism.
  }

  private void waitForStart(IOThread iot, IOThread eot) throws InterruptedException {
    // read the "START" to let us know the JVM has started
    // we don't want to time this.
    // FIXME this looks dangerous. What if the test can't even get to the point of outputting START (e.g. class loading issues)
    while (true) {
      String out = iot.getNext();
      String err = eot.getAvailable();
      if (mVerbose) {
        debugOutput(out, err);
      }
      if ((out == null) && (err == null)) {
        Thread.sleep(10);
      } else if ("START".equals(out)) {
        break;
      } else {
        throw new RuntimeException("jumble.fast.FastJumbler returned "
                                   + ((out != null) ? out : err + " on stderr") 
                                   + " instead of START");
      }
    }
  }


  /**
   * Performs a Jumble run on the specified class with the specified tests.
   * 
   * @param className the name of the class to Jumble
   * @param testClassNames the names of the associated test classes
   * @return the results of the Jumble run
   * @throws Exception if something goes wrong
   * @see JumbleResult
   */
  public JumbleResult runJumble(final String className, final List testClassNames) throws Exception {
    mClassName = className;
    long ts = System.currentTimeMillis();
    mCacheFileName = "cache" + ts + ".dat";
    mTestSuiteFileName = "testSuite" + ts + ".dat";
    
    if (mUseCache) {
      initCache();
    }

    final int mutationCount = countMutationPoints();
    if (mutationCount == -1) {
      return new InterfaceResult(className); 
    }

    Class[] testClasses = new Class[testClassNames.size()];
    for (int i = 0; i < testClasses.length; i++) {
      try {
        testClasses[i] = Class.forName((String) testClassNames.get(i));
      } catch (ClassNotFoundException e) {
        // test class did not exist
        return new FailedTestResult(className, testClassNames, null, mutationCount);
      }
    }

    final TestResult initialResult = new TestResult();
    final TimingTestSuite timingSuite = new TimingTestSuite(testClasses);
    assert Debug.println("Parent. Starting initial run without mutating");
    timingSuite.run(initialResult);
    assert Debug.println("Parent. Finished");
    // Now, if the tests failed, can return straight away
    if (!initialResult.wasSuccessful()) {
      return new FailedTestResult(className, testClassNames, initialResult, mutationCount);
    }

    // Store the test suite information serialized in a temporary file
    final TestOrder order = timingSuite.getOrder(mOrdered);
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(mTestSuiteFileName));
    oos.writeObject(order);
    oos.close();

    // compute the timeout
    long totalRuntime = timingSuite.getTotalRuntime();

    final JavaRunner runner = new JavaRunner("jumble.fast.FastJumbler");
    Process childProcess = null;
    IOThread iot = null;
    IOThread eot = null;
    
    final Mutation[] allMutations = new Mutation[mutationCount];
    for (int currentMutation = 0; currentMutation < mutationCount; currentMutation++) {
      // If no process is running, start a new one
      if (childProcess == null) {
        // start process
        runner.setArguments(createArgs(currentMutation));
        childProcess = runner.start();
        iot = new IOThread(childProcess.getInputStream());
        iot.start();
        eot = new IOThread(childProcess.getErrorStream());
        eot.start();
        
        waitForStart(iot, eot);
      }
      long before = System.currentTimeMillis();
      long after = before;
      long timeout = computeTimeout(totalRuntime);
      // Run until we time out
      while (true) {
        String out = iot.getNext();
        String err = eot.getAvailable();
        if (mVerbose) {
          debugOutput(out, err);
        }
        if (out == null) {
          if (after - before > timeout) {
            allMutations[currentMutation] = new Mutation("TIMEOUT", className, currentMutation);
            childProcess.destroy();
            childProcess = null;
            break;
          } else {
            Thread.sleep(50);
            after = System.currentTimeMillis();
          }
        } else {
          // We have output so go to the next mutation
          allMutations[currentMutation] = new Mutation(out, className, currentMutation);
          if (mUseCache) {
            updateCache(allMutations[currentMutation]);
          }
          break;
        }
      }
    }

    JumbleResult ret = new NormalJumbleResult(className, testClassNames, initialResult, allMutations, computeTimeout(totalRuntime));
    // finally, delete the test suite file
    if (!new File(mTestSuiteFileName).delete()) {
      System.err.println("Error: could not delete temporary file");
    }
    // Also delete the temporary cache and save the cache if needed
    if (mUseCache) {
      if (!new File(mCacheFileName).delete()) {
        System.err.println("Error: could not delete temporary cache file");
      }
      if (mSaveCache) {
        writeCache(CACHE_FILENAME);
      }
    }
    mCache = null;
    return ret;
  }
}

