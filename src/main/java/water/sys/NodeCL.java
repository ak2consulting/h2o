package water.sys;

import java.io.File;
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
    setDaemon(true);
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
    run(true);
  }

  public void run(boolean close) {
    _initialClassLoader = Thread.currentThread().getContextClassLoader();
    _classLoader = new URLClassLoader(_classpath, null);
    Thread.currentThread().setContextClassLoader(_classLoader);
    Exception ex = null;

    try {
      Class c = _classLoader.loadClass(Boot.class.getName());
      Method method = c.getMethod("main", new Class[] { String[].class });
      method.invoke(null, (Object) _args);
    } catch( Exception e ) {
      Log.write(e);
      ex = e;
    }

    Thread.currentThread().setContextClassLoader(_initialClassLoader);

    if( ex != null && !(ex.getCause() instanceof InterruptedException) )
      throw new RuntimeException(ex.getCause());

    if( close )
      kill();
  }

  @Override
  public void kill() {
    invoke(NodeCL.class.getName(), "close_", new Class[0]);
  }

  public static void close_() {
    TestUtil.checkLeakedKeys();
  }

  private Object invoke(String className, String methodName, Class[] argsTypes, Object... args) {
    assert Thread.currentThread().getContextClassLoader() == _initialClassLoader;
    Thread.currentThread().setContextClassLoader(_classLoader);

    try {
      Class<?> c = _classLoader.loadClass(className);
      Method method = c.getMethod(methodName, argsTypes);
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
