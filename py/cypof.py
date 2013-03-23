from water.sys.Jython import browser
import h2o, h2o_cmd, h2o_hosts
import time

# Boot.main([]);
# key = External.makeKey("irisModel");

print 'cypof'
#h2o.build_cloud_in_process()
#h2o.build_cloud(node_count=3)

#h2o.config_json = "pytest_config-cypof.json"
#h2o.config_json = "pytest_config-150-155.json"
#h2o_hosts.build_cloud_with_hosts()

h2o_cmd.runKMeans(csvPathname='smalldata/covtype/covtype.20k.data', key='covtype', k=7)
n = h2o.nodes[0]
#browser.open("http://" + n.node.address() + ":" + str(n.port) + "/Inspect.html?key=covtype")
#time.sleep(1)
h2o.tear_down_cloud()
