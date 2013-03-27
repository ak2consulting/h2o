import os, json, unittest, time, shutil, sys, time
sys.path.extend(['.','..','py'])

import h2o_cmd, h2o, h2o_hosts
import h2o_browse as h2b
import time

node_count = 5
global base_port
base_port = 50550
portsPerNode = 2

def check_cloud_and_setup_next():
    h2b.browseTheCloud()
    h2o.verify_cloud_size()
    h2o.check_sandbox_for_errors()
    print "Tearing down cloud of size", len(h2o.nodes)
    h2o.tear_down_cloud()
    h2o.clean_sandbox()
    # wait to make sure no sticky ports or anything os-related
    # so let's expand the delay if larger number of jvms
    # 1 second per node seems good
    h2o.verboseprint("Waiting", node_count, "seconds to avoid OS sticky port problem")
    time.sleep(node_count)
    # stick port issues (os)
    # wait a little for jvms to really clear out?
    # and increment the base_port
    ### global base_port
    ### base_port += portsPerNode * node_count

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        # do the first one to build up hosts
        # so we don't repeatedly copy the jar
        # have to make sure base_port is the same on both!
        print "base_port:", base_port
        h2o.check_port_group(base_port)
        start = time.time()
        h2o_hosts.build_cloud_with_hosts(node_count, base_port=base_port, 
            use_multicast=False, java_heap_GB=1)
        print "Cloud of", len(h2o.nodes), "built in", time.time()-start, "seconds"
        # have to remember total # of nodes for the next class. it will stay the same
        # when we tear down the cloud, we zero the nodes list
        global totalNodes
        totalNodes = len(h2o.nodes)
        check_cloud_and_setup_next()

    @classmethod
    def tearDownClass(cls):
        pass

    def test_remote_cloud(self):
        global base_port
        # FIX! we should increment this from 1 to N? 
        for i in range(1,10):
            # timeout wants to be larger for large numbers of hosts * node_count
            ### base_port += portsPerNode * node_count
            print "base_port:", base_port
            timeoutSecs = max(60, 8 * totalNodes)
            print "totalNodes:", totalNodes, "timeoutSecs:", timeoutSecs

            h2o.check_port_group(base_port)
            
            start = time.time()
            h2o.build_cloud(node_count, base_port=base_port, hosts=h2o_hosts.hosts, use_multicast=True, 
                timeoutSecs=timeoutSecs, retryDelaySecs=0.5)
            print "Cloud of", len(h2o.nodes), "built in", time.time()-start, "seconds"
            check_cloud_and_setup_next()


if __name__ == '__main__':
    h2o.unit_main()

