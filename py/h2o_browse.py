import h2o
import webbrowser, re
# just some things useful for debugging or testing. pops the brower and let's you look at things
# like the confusion matrix by matching the RFView json (h2o keeps the json history for us)


# always nice to have a browser up on the cloud while running at test. You can see the fork/join task state
# and browse to network stats, or look at the time line. Starting from the cloud page is sufficient.
def browseTheCloud():
    # after cloud building, node[0] should have the right info for us
    cloud_url = "http://" + h2o.nodes[0].addr + ":" + str(h2o.nodes[0].port)
    # Open URL in new window, raising the window if possible.
    webbrowser.open_new(cloud_url)

def browseJsonHistoryAsUrlLastMatch(matchme):
    # get rid of the ".json" from the last url used by the test framework.
    # if we hit len(), we point to 0, so stop
    len_history= len(h2o.json_url_history)
    i = -1
    while (len_history+i!=0 and not re.search(matchme,h2o.json_url_history[i]) ):
        i = i - 1
    json_url = h2o.json_url_history[i]

    # chop out the .json to get a browser-able url (can look at json too)
    # Open URL in new window, raising the window if possible.
    # webbrowser.open_new_tab(json_url)
    url = re.sub(".json","",json_url)
    webbrowser.open_new_tab(url)

# maybe not useful, but something to play with.
# go from end, backwards and see what breaks! (in json to html hack url transform)
# note that put/upload  and rf/rfview methods are different for html vs json
def browseJsonHistoryAsUrl():
    ignoring = "Cloud"
    i = -1
    # stop if you get to -50, don't want more than 50 tabs on browser
    tabCount = 0
    while (tabCount<50 and len_history+i!=0):
        i = i - 1
        # ignore the Cloud "alive" views
        # FIX! we probably want to expand ignoring to more than Cloud?
        if not re.search(ignoring,h2o.json_url_history[i]):
            json_url = h2o.json_url_history[i]
            url = re.sub(".json","",json_url)
            webbrowser.open(url)
            tabCount += 1