# from H2OInit import Boot, External
from water.sys.Jython import browser
import h2o
import h2o_cmd
import time

# Boot.main([]);
# key = External.makeKey("irisModel");

print 'cypof'
h2o.build_cloud_in_process()
h2o_cmd.runKMeans(csvPathname='smalldata/covtype/covtype.20k.data', key='covtype', k=7)
browser.open("http://localhost:54321/Inspect.html?key=covtype")
time.sleep(1)
h2o.tear_down_cloud()
