import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd
import h2o_browse as h2b


def write_syn_dataset(csvPathname, rowCount, headerData, rowData):
    dsf = open(csvPathname, "w+")
    
    dsf.write(headerData + "\n")
    for i in range(rowCount):
        dsf.write(rowData + "\n")
    dsf.close()

# append!
def append_syn_dataset(csvPathname, rowData):
    with open(csvPathname, "a") as dsf:
        dsf.write(rowData + "\n")

class glm_same_parse(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # fails with 3
        global local_host
        local_host = not 'hosts' in os.getcwd()
        if (local_host):
            h2o.build_cloud(3,java_heap_GB=4,use_flatfile=True)
        else:
            h2o_hosts.build_cloud_with_hosts()

        h2b.browseTheCloud()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud(h2o.nodes)
    
    def test_sort_of_prostate_with_row_schmoo(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        csvFilename = "syn_prostate.csv"
        csvPathname = SYNDATASETS_DIR + '/' + csvFilename

        headerData = "ID,CAPSULE,AGE,RACE,DPROS,DCAPS,PSA,VOL,GLEASON"
        rowData = "1,0,65,1,2,1,1.4,0,6"

        write_syn_dataset(csvPathname,      99860, headerData, rowData)

        print "This is the same format/data file used by test_same_parse, but the non-gzed version"
        print "\nSchmoo the # of rows"
        print "Updating the key and key2 names for each trial"
        for trial in range (200):
            append_syn_dataset(csvPathname, rowData)
            ### start = time.time()
            # this was useful to cause failures early on. Not needed eventually
            ### key = h2o_cmd.parseFile(csvPathname=h2o.find_file("smalldata/logreg/prostate.csv"))
            ### print "Trial #", trial, "parse end on ", "prostate.csv" , 'took', time.time() - start, 'seconds'

            start = time.time()
            key = csvFilename + "_" + str(trial)
            key2 = csvFilename + "_" + str(trial) + ".hex"
            key = h2o_cmd.parseFile(csvPathname=csvPathname, key=key, key2=key2)
            print "trial #", trial, "parse end on ", csvFilename, 'took', time.time() - start, 'seconds'

            h2o_cmd.runInspect(key=key2)
            # only used this for debug to look at parse (red last row) on failure
            ### h2b.browseJsonHistoryAsUrlLastMatch("Inspect")
            
            if (h2o.check_sandbox_for_errors()):
                raise Exception("Found errors in sandbox stdout or stderr, on trial #%s." % trial)

if __name__ == '__main__':
    h2o.unit_main()
