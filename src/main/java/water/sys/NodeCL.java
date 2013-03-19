package water.sys;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

import water.*;

/**
 * Creates a node in-process using a separate class loader.
 */
public class NodeCL extends Thread implements Node {
  private final URL[]    _classpath;
  private final String[] _args;
  private ClassLoader    _initialClassLoader, _classLoader;

  public NodeCL(String[] args) {
    _args = args;
    _classpath = getClassPath();
    _initialClassLoader = Thread.currentThread().getContextClassLoader();
    _classLoader = new URLClassLoader(_classpath, null);

    setDaemon(true);
  }

  @Override
  public String address() {
    return Log.HOST; // TODO cache elsewhere
  }

  @Override
  public void inheritIO() {
    // TODO add -id to PID?
    // invoke(className, methodName, args)
  }

  @Override
  public void persistIO(String outFile, String errFile) throws IOException {
    // TODO
    // invoke(className, methodName, args)
  }

  static URL[] getClassPath() {
    String[] classpath = System.getProperty("java.class.path").split(File.pathSeparator);
    try {
      final List<URL> list = new ArrayList<URL>();
      if( classpath != null ) {
        for( final String element : classpath ) {
          list.addAll(getDirectoryClassPath(element));
          list.add(new File(element).toURI().toURL());
        }
      }
      return list.toArray(new URL[list.size()]);
    } catch( Exception e ) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int waitFor() {
    try {
      join();
      return 0;
    } catch( InterruptedException ex ) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void run() {
    invoke(Boot.class.getName(), "main", (Object) _args);
  }

  @Override
  public void kill() {
    invoke(NodeCL.class.getName(), "close_");
  }

  public static void close_() {
    TestUtil.checkLeakedKeys();
  }

  private Object invoke(String className, String methodName, Object... args) {
    assert Thread.currentThread().getContextClassLoader() == _initialClassLoader;
    Thread.currentThread().setContextClassLoader(_classLoader);

    Class[] types = new Class[args.length];
    for( int i = 0; i < args.length; i++ )
      types[i] = args[i].getClass();

    try {
      Class<?> c = _classLoader.loadClass(className);
      Method method = c.getMethod(methodName, types);
      method.setAccessible(true);
      return method.invoke(null, args);
    } catch( Exception ex ) {
      throw new RuntimeException(ex);
    } finally {
      Thread.currentThread().setContextClassLoader(_initialClassLoader);
    }
  }

  private static List<URL> getDirectoryClassPath(String aDir) {
    try {
      final List<URL> list = new LinkedList<URL>();
      final File dir = new File(aDir);
      final URL directoryURL = dir.toURI().toURL();
      final String[] children = dir.list();

      if( children != null ) {
        for( final String element : children ) {
          if( element.endsWith(".jar") ) {
            final URL url = new URL(directoryURL, URLEncoder.encode(element, "UTF-8"));
            list.add(url);
          }
        }
      }
      return list;
    } catch( Exception e ) {
      throw new RuntimeException(e);
    }
  }
}
