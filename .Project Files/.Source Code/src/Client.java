import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class Client {

    // User Arguments
    static boolean manualInput = false; // Allow manual input of commands
    static boolean help        = false; // Display program usage
    static boolean verbose     = false;

    // Automation variables
    static int algorithm = 0; // 0 = AllToLargest, 1 = First-Fit, 2 = Best-Fit, 3 = Worst-Fit, 4 = Best-Cost

    /**
     * Data regarding regarding structure of servers, information about status, and number of servers per type.
     */

    // Real time information on status of each server.
    ArrayList<ArrayList<String>> allServerInfo = new ArrayList<>();

    // Copy of allServerInfo is saved on initial RESCAll scan.
    ArrayList<ArrayList<String>> initialAllServerInfo = new ArrayList<>();

    // Format of ArrayList<String> = {serverType, coreCount, numberOfServersOfThisType}
    ArrayList<ArrayList<String>> sortOrder = new ArrayList<>();

    public static void main(String args[]) {

        // Process program arguments
        for(int i = 0; i < args.length; i++) {

            if(args[i].equals("-m")) { manualInput = true; } // Enable manual input from user

            else if(args[i].equals("-h")) {  help = true; } // Display program usage

            else if(args[i].equals("-a")) { // Specify algorithm to be used

                if(args[i+1].equals("ff"))
                    algorithm = 1;
                else if(args[i+1].equals("bf"))
                    algorithm = 2;
                else if(args[i+1].equals("wf"))
                    algorithm = 3;
                else if(args[i+1].equals("bc"))
                    algorithm = 4;
                else {
                    System.out.println("Please enter a valid algorithm.");
                    help = true;
                }

                i++;

            }

            else if(args[i].equals("-v")) {
                verbose = true;
            }

        }

        System.out.println("# ds-sim client S1-07May, 2019 (COMP335@MQ)");
        System.out.println("# Created by Mohamed Maatouk, Burak Ozturker & Cassandra Aung");

        if(!verbose) {
            System.out.println("#");
            System.out.println("#   /$$$$$$  /$$ /$$                       /$$            /$$$$$$  /$$");
            System.out.println("#   /$$__  $$| $$|__/                      | $$           /$$__  $$|__/");
            System.out.println("#  | $$  \\__/| $$ /$$  /$$$$$$  /$$$$$$$  /$$$$$$        | $$  \\__/ /$$ /$$$$$$/$$$$");
            System.out.println("#  | $$      | $$| $$ /$$__  $$| $$__  $$|_  $$_/        |  $$$$$$ | $$| $$_  $$_  $$");
            System.out.println("#  | $$      | $$| $$| $$$$$$$$| $$  \\ $$  | $$           \\____  $$| $$| $$ \\ $$ \\ $$");
            System.out.println("#  | $$    $$| $$| $$| $$_____/| $$  | $$  | $$ /$$       /$$  \\ $$| $$| $$ | $$ | $$|");
            System.out.println("#  |  $$$$$$/| $$| $$|  $$$$$$$| $$  | $$  |  $$$$/      |  $$$$$$/| $$| $$ | $$ | $$");
            System.out.println("#   \\______/ |__/|__/ \\_______/|__/  |__/   \\___/         \\______/ |__/|__/ |__/ |__/");
            System.out.println();
        }


        if(help)
            clientUsage();
        else {
            Client client = new Client("127.0.0.1", 8096);
        }

    }

    // initialize socket and input output streams
    private Socket socket               = null;
    private BufferedReader in           = null;
    private DataOutputStream out        = null;

    public Client(String address, int port) {
        // establish a connection
        try {

            // Connect to the server
            socket = new Socket(address, port);

            // Commands received from server
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Commands sent to the server
            out = new DataOutputStream(socket.getOutputStream());

        } catch(UnknownHostException u) { System.out.println(u);
        } catch(IOException i) { System.out.println(i); }

        // Manual input of commands.
        if(manualInput) {

            // Scanner initialisation for user manual input
            Scanner in = new Scanner(new InputStreamReader(System.in));
            String currentCommand = "";

            while(!currentCommand.equals("QUIT")) {

                // Enter and send a command to the Server.
                currentCommand = in.nextLine();
                sendCommand(currentCommand);

            }

        }

        // Automated input of commands.
        else {

            // Say Hello and sign in to Server.
            ClientSetup();

            // Schedule all jobs based on default algorithm unless specified otherwise.
            ClientScheduler();

            // Close connection to server once all jobs have been scheduled.
            sendCommand("QUIT");

        }

        try {

            out.close();
            socket.close();
            in.close();

        } catch(IOException i) { System.out.println(i); }

    }

    public void ClientSetup() {

        // Say Hello and sign in
        sendCommand("HELO");
        sendCommand("AUTH "+System.getProperty("user.name"));

    }

    /**
     * Client Scheduler
     * If no algorithm was specified using "-a" then the default is used (allToLargest).
     *
     * Some details regarding the format for jobs and servers
     *
     *      JOBN submit_time (int) job_ID (int) estimated_runtime (int) #CPU_cores (int) memory (int) disk(int)
     *      0    1                 2            3                       4                5            6
     *
     *      server_type (char *) server_ID (int) server_state (int) available_time (int) #CPU_cores (int) memory (int) disk_space (int)
     *      0                    1               2                  3                    4                5            6
     */
    public void ClientScheduler() {

        String currentJob = sendCommand("REDY");
        String[] currentJobDetails = currentJob.split(" ");
        String[] status = {""};

        // Save an initial copy of all server info
        RESCAll();
        initialAllServerInfo = allServerInfo;

        // Save another copy so that data in both ArrayLists are not linked
        RESCAll();
        int indexOfLargestServer = 0;
        for(int i = 0; i < allServerInfo.size(); i++) {
            if( Integer.parseInt(allServerInfo.get(indexOfLargestServer).get(4)) < Integer.parseInt(allServerInfo.get(i).get(4)) )
                indexOfLargestServer = i;
        }

        findAllServerInfoSortOrder();
        for(ArrayList<String> list: sortOrder)
            System.out.println(list);

        while(!currentJob.equals("NONE") && !status[0].equals("ERR:")) {

            currentJobDetails = currentJob.split(" ");
            String serverType = "";
            String serverID = "";

            // Collect information on all servers
            RESCAll();

            // AllToLargest
            if (algorithm == 0) {
                serverType = allServerInfo.get(indexOfLargestServer).get(0);
                serverID = "0";
            }

            // First-Fit
            else if (algorithm == 1) {

                // Sort All Servers from smallest to largest
                allServerInfo = sortAllServerInfo(allServerInfo);
                initialAllServerInfo = sortAllServerInfo(initialAllServerInfo);

                ArrayList<String> firstFitServer = findFirstFit(currentJobDetails);
                serverType = firstFitServer.get(0);
                serverID = firstFitServer.get(1);

            }

            // Best-Fit
            else if (algorithm == 2) {

                ArrayList<String> bestFitServer = findBestFit(currentJobDetails);
                serverType = bestFitServer.get(0);
                serverID = bestFitServer.get(1);

            }

            // Worst-Fit
            else if (algorithm == 3) {

                ArrayList<String> worstFitServer = findWorstFit(currentJobDetails);
                serverType = worstFitServer.get(0);
                serverID = worstFitServer.get(1);

            }

            // Best-Cost
            else if (algorithm == 4) {

                // Sort All Servers from smallest to largest
                allServerInfo = sortAllServerInfo(allServerInfo);
                initialAllServerInfo = sortAllServerInfo(initialAllServerInfo);

                ArrayList<String> bestCostServer = findBestCost(currentJobDetails);
                serverType = bestCostServer.get(0);
                serverID = bestCostServer.get(1);

            }

            // Run the job
            status = sendCommand("SCHD " +
                            currentJobDetails[2] + " " +
                                serverType + " " +
                                    serverID).split(" ");

            // Goto next job
            currentJob = sendCommand("REDY");

        }

    }

    // Client Scheduler Algorithms

    /**
     * First-Fit Algorithm
     * @return the first server with sufficient resources to run the job, if none are found, check based on initial
     * resource capacity.
     */
    public ArrayList<String> findFirstFit(String[] currentJob) {

        // Traverse through all servers
        for(ArrayList<String> server: allServerInfo) {

            // Return the first server with sufficient resources
            if(hasSufficientResources(server, currentJob)) {
                return server;
            }

        }

        // Traverse through all servers based on initial check of resources
        for(int i = 0; i < initialAllServerInfo.size(); i++) {

            // Return the first server with sufficient resources and is active --> serverState = 3 (Active / Busy)
            if(hasSufficientResources(initialAllServerInfo.get(i), currentJob)) {
                if(isServerActive(allServerInfo.get(i))) {
                    return allServerInfo.get(i);
                }
            }

        }

        // If null is returned, no server exists that has sufficient resources to start the job
        return null;

    }

    /**
     * Best-Fit Algorithm
     * @return the best-fit server that has sufficient resources to run the job, if none are found, return the best-fit
     * active server based on initial resource capacity
     *
     * The best-fit is calculated using a fitness value, this fitness value is the difference between the server's
     * number of cores and the job's required number of cores. A server is considered the best-fit when this difference
     * is closer to 0 (Can only be positive).
     *
     */
    public ArrayList<String> findBestFit(String[] currentJob) {

        int bestFit = Integer.MAX_VALUE, minAvail = Integer.MAX_VALUE;
        ArrayList<String> bestFitServer = null;

        // Traverse through all servers
        for(ArrayList<String> server: allServerInfo) {

            // Server must have sufficient resources to run the job
            if(hasSufficientResources(server, currentJob)) {

                int fitnessValue = calculateFitnessValue(server, currentJob);
                int serverAvail = Integer.parseInt(server.get(3));

                /**
                 * If there is a server with a lower fitness value set that server as the server with the best-fit,
                 * otherwise if there is a server with the same fitness value but is available in a shorter amount of
                 * time set that server as the server with best-fit.
                 */
                if( (fitnessValue < bestFit) || (fitnessValue == bestFit && serverAvail < minAvail) ) {

                    bestFit = fitnessValue;
                    minAvail = serverAvail;
                    bestFitServer = server;

                }

            }

        }

        if(bestFitServer != null) {
            return bestFitServer;
        } else {

            bestFit = Integer.MAX_VALUE;
            bestFitServer = null;

            // Traverse through all servers based on initial check of resources
            for(int i = 0; i < initialAllServerInfo.size(); i++) {

                ArrayList<String> initialServer = initialAllServerInfo.get(i);
                ArrayList<String> currentServer = allServerInfo.get(i);

                // Server must have sufficient resources to run the job
                if(hasSufficientResources(initialServer, currentJob)) {

                    int fitnessValue = calculateFitnessValue(initialServer, currentJob);

                    // The server's available time is not checked since all active servers are busy
                    if( (fitnessValue < bestFit) && isServerActive(currentServer)) {

                        bestFit = fitnessValue;
                        bestFitServer = initialServer;

                    }

                }

            }

            return bestFitServer;

        }


    }

    /**
     * Worst-Fit Algorithm
     * @return the worst-fit server that has sufficient resources to run the job, if none are found, return the second
     * worst-fit (altFit), if none are found, return the worst-fit active server based on initial resource capacity.
     *
     * The worst-fit is calculated similarly to best-fit in that a fitness value is calculated, however the worst-fit
     * server is found when a server has the biggest fitness value (Larger the better).
     *
     */
    public ArrayList<String> findWorstFit(String[] currentJob) {

        int worstFit = Integer.MIN_VALUE;
        ArrayList<String> worstFitServer = null;

        int altFit = Integer.MIN_VALUE;
        ArrayList<String> altFitServer = null;

        // Traverse through all servers
        for(ArrayList<String> server: allServerInfo) {

            // Server must have sufficient resources to run the job
            if(hasSufficientResources(server, currentJob)) {

                int fitnessValue = calculateFitnessValue(server, currentJob);

                /**
                 * If there is a server with a higher fitness value and it is immediately available set that server as
                 * the server with the worst-fit
                 */
                if( (fitnessValue > worstFit) && isServerImmediatelyAvailable(server) ) {

                    worstFit = fitnessValue;
                    worstFitServer = server;

                }

                /**
                 * If there is a server with a higher fitness value that is not immediately available (Inactive State)
                 * set that server as the server with the second worst-fit
                  */
                else if (fitnessValue > altFit && !isServerImmediatelyAvailable(server)) {

                    altFit = fitnessValue;
                    altFitServer = server;

                }

            }

        }

        if(worstFitServer != null) {
            return worstFitServer;
        } else if(altFitServer != null) {
            return altFitServer;
        } else {

            /**
             * If no worst-fit server has been found (No server with sufficient resources has been found), find the
             * server with the worst-fit based on initial resource capacity.
              */

            worstFit = Integer.MIN_VALUE;
            worstFitServer = null;

            // Traverse through all servers based on initial check of resources
            for(int i = 0; i < initialAllServerInfo.size(); i++) {

                ArrayList<String> initialServer = initialAllServerInfo.get(i);
                ArrayList<String> currentServer = allServerInfo.get(i);

                // Server must have sufficient resources to run the job
                if(hasSufficientResources(initialServer, currentJob)) {

                    int fitnessValue = calculateFitnessValue(initialServer, currentJob);

                    if( fitnessValue > worstFit && isServerActive(currentServer) ) {

                        worstFit = fitnessValue;
                        worstFitServer = initialServer;

                    }

                }

            }

            return worstFitServer;
        }

    }

    // Hawk Algorithm Variables
    boolean isSetup = false;
    boolean isReservedSmall = false;
    ArrayList<ArrayList<String>> reservedServers = new ArrayList<>();

    /**
     * Best-Cost Algorithm - Extends First Fit Algorithm.
     *
     * The Algorithm is exactly like first fit except when a situation arises when all active servers are full, the
     * lgorithm then calculates a wait time for each server:
     *
     *      - -1     FREE          0SEC
     *      -  0     INSTANT       1SEC        till        10SECS
     *      -  1     SHORT         11SECS      till        5MINS
     *      -  2     MEDIUM        5MINS       till        1HR
     *      -  3     LONG          1HR         till        12HRS
     *      -  4     PERMANENT     12HRS       till        24855 Days
     *
     */
    public ArrayList<String> findBestCost(String[] currentJob) {

        /**
         * We want to know the number of largest servers so we can determine how many servers to reserve for both:
         * INSTANT -> MEDIUM Tasks, and LONG tasks.
         */
        if(!isSetup) {

            int noLargestServers = Integer.parseInt(sortOrder.get(sortOrder.size()-1).get(2));

            if (noLargestServers >= 2) {

                /**
                 * Reserve one server: one for INSTANT -> MEDIUM Tasks
                 */

                isReservedSmall = true;

                String largestServerType = sortOrder.get(sortOrder.size()-1).get(0);

                reservedServers.add(
                        initialAllServerInfo.get(
                                findServerIndex(largestServerType, Integer.toString(0))));

            } else if(sortOrder.size() > 1) {

                /**
                 * Reserve one of the second largest servers: one for INSTANT -> MEDIUM TASKS
                 * Jobs that require the the largest server are put with long jobs.
                 */

                isReservedSmall = true;

                String largestServerType = sortOrder.get(sortOrder.size()-2).get(0);

                reservedServers.add(
                        initialAllServerInfo.get(
                                findServerIndex(largestServerType, Integer.toString(0))));

            }

            isSetup = true;

        }

        int currentJobWaitTime = findJobWaitTime(currentJob);

        if(currentJobWaitTime < 3 && isReservedSmall && hasSufficientResources(reservedServers.get(0), currentJob)) {

            return reservedServers.get(0);

        } else {

            return findServerWithShortestWaitTime(currentJob);

        }

    }

    // Algorithm Helper Methods

    public int findServerIndex(String serverType, String serverID) {

        for(int i = 0; i < allServerInfo.size(); i++) {

            String selectedServerType = allServerInfo.get(i).get(0);
            String selectedServerID = allServerInfo.get(i).get(1);

            if(selectedServerType.equals(serverType) && selectedServerID.equals(serverID)) {
                return i;
            }

        }

        return -1;

    }

    /**
     * Find a server to run a long job on.
     */
    public ArrayList<String> findServerWithShortestWaitTime(String[] currentJob) {

        ArrayList<String> bestFitServer = findModifiedBestFit(currentJob);

        // Either there are no active or available server, or all currently active servers are full
        if(bestFitServer == null) {

            ArrayList<String> worstFitServer = findModifiedWorstFit(currentJob);

            // If there is at least one server running.
            if(worstFitServer == null) {

                return findServerWithSmallestQueue(currentJob);

            } else {

                return worstFitServer;

            }

        }

        // Immediately schedule job to run instantly.
        return bestFitServer;

    }

    /**
     * Find the best-fit server ignoring reserved servers.
     */
    public ArrayList<String> findModifiedBestFit(String[] currentJob) {

        int bestFit = Integer.MAX_VALUE;
        ArrayList<String> bestFitServer = null;

        // Traverse through all servers
        for(ArrayList<String> server: allServerInfo) {

            // Server must have sufficient resources to run the job
            if(hasSufficientResources(server, currentJob)) {

                int fitnessValue = calculateFitnessValue(server, currentJob);
                int serverAvail = Integer.parseInt(server.get(3));

                /**
                 * Find best-fit based on currently available / active servers ignoring reserved servers.
                 */
                if( (fitnessValue < bestFit) && isServerActive(server) && !isReserved(server)) {

                    bestFit = fitnessValue;
                    bestFitServer = server;

                }

            }

        }

        // If return is null, no active server with available resources was found.
        return bestFitServer;

    }

    public ArrayList<String> findModifiedWorstFit(String[] currentJob) {

        int worstFit = Integer.MIN_VALUE;
        ArrayList<String> worstFitServer = null;

        // Traverse through all servers
        for(ArrayList<String> server: allServerInfo) {

            // Server must have sufficient resources to run the job
            if(hasSufficientResources(server, currentJob)) {

                int fitnessValue = calculateFitnessValue(server, currentJob);

                /**
                 * If there is a server with a higher fitness value and it is immediately available set that server as
                 * the server with the worst-fit
                 */
                if( (fitnessValue > worstFit) && !isReserved(server) ) {

                    worstFit = fitnessValue;
                    worstFitServer = server;

                }

            }

        }

        return worstFitServer;

    }

    public ArrayList<String> findServerWithSmallestQueue(String[] currentJob) {

        int serverWithLowestJobCount = Integer.MAX_VALUE;
        ArrayList<String> chosenServer = null;

        for(int i = 0; i < allServerInfo.size(); i++) {

            ArrayList<String> server = allServerInfo.get(i);
            ArrayList<String> initialServer = initialAllServerInfo.get(i);
            int serverJobCount = findNumberOfJobsOnServer(server);

            if( (serverJobCount < serverWithLowestJobCount) && !isReserved(server) && hasSufficientResources(initialServer, currentJob)) {

                serverWithLowestJobCount = serverJobCount;
                chosenServer = server;

            }

        }

        return chosenServer;

    }

    public int findNumberOfJobsOnServer(ArrayList<String> server) {

        if(isServerIdle(server) || !isServerImmediatelyAvailable(server)) {
            return 0;
        }

        int retVal = 0;

        // Request list of jobs from server
        sendCommandNoLog("LSTJ "+server.get(0)+" "+server.get(1));
        String serverResponse = sendCommandNoLog("OK");

        while(!serverResponse.equals(".")) {

            retVal++;

            serverResponse = sendCommandNoLog("OK");

        }

        return retVal;

    }
    public int findWaitTimeOnServer(ArrayList<String> server) {

        if(isServerIdle(server) || !isServerImmediatelyAvailable(server)) {
            return 0;
        }

        int retVal = 0;

        // Request list of jobs from server
        sendCommandNoLog("LSTJ "+server.get(0)+" "+server.get(1));
        String serverResponse = sendCommandNoLog("OK");

        while(!serverResponse.equals(".")) {

            String[] job = serverResponse.split(" ");

            retVal += Integer.parseInt(job[2]);

            serverResponse = sendCommandNoLog("OK");

        }

        return retVal;

    }

    public boolean isReserved(ArrayList<String> server) {

        String selectedServerType = server.get(0);
        String selectedServerID = server.get(1);

        for(ArrayList<String> reservedServer: reservedServers) {

            String reservedServerType = reservedServer.get(0);
            String reservedServerID = reservedServer.get(1);

            if(selectedServerType.equals(reservedServerType) && selectedServerID.equals(reservedServerID))
                return true;

        }

        return false;

    }

    public boolean isAnyServerImmediatelyAvailable() {

        for(ArrayList<String> server: allServerInfo) {
            if(isServerImmediatelyAvailable(server))
                return true;
        }

        return false;

    }

    public boolean isServerActive(ArrayList<String> server) {

        int serverState = Integer.parseInt(server.get(2));

        if(serverState == 3)
            return true;

        return false;

    }

    public boolean isServerIdle(ArrayList<String> server) {

        int serverState = Integer.parseInt(server.get(2));

        if(serverState == 2)
            return true;

        return false;

    }

    public int calculateServerWaitTime(ArrayList<String> server) {

        // Server is instantly available when idle
        if(isServerIdle(server))
            return 0;

        // Request list of jobs from server
        sendCommandNoLog("LSTJ "+server.get(0)+" "+server.get(1));
        int largestJobWaitTime = 0;

        String serverResponse = sendCommandNoLog("OK");
        while(!serverResponse.equals(".")) {

            // Split server response into list of words
            String[] tempJob = serverResponse.split(" ");

            int currentJobWaitTime = findJobWaitTime(tempJob);

            if(currentJobWaitTime > largestJobWaitTime)
                largestJobWaitTime = currentJobWaitTime;

            // Goto the next job.
            serverResponse = sendCommandNoLog("OK");

        }

        // 0 = INSTANT (BEST), 1 = SHORT, 2 = MEDIUM, 3 = LONG, 4 = PERMANENT (WORST)
        return largestJobWaitTime;

    }

    public int calculateServerJobs(ArrayList<String> server) {

        int retVal = 0;

        // Server is instantly available when idle
        if(isServerIdle(server))
            return retVal;

        // Request list of jobs from server
        sendCommandNoLog("LSTJ "+server.get(0)+" "+server.get(1));
        String serverResponse = sendCommandNoLog("OK");

        while(!serverResponse.equals(".")) {

            // Split server response into list of words
            String[] tempJob = serverResponse.split(" ");

            retVal++;

            // Goto the next job.
            serverResponse = sendCommandNoLog("OK");

        }

        return retVal;

    }

    /**
     * Jobs are classified as one of the following:
     *
     *      - 0     INSTANT       1SEC        till        10SECS
     *      - 1     SHORT         11SECS      till        5MINS
     *      - 2     MEDIUM        5MINS       till        1HR
     *      - 3     LONG          1HR         till        12HRS
     *      - 4     PERMANENT     12HRS       till        24855 Days
     *
     * If -1 is returned the job has a runtime of nothing, it doesn't exist.
     */
    public int findJobWaitTime(String[] currentJob) {

        int currentJobWaitTime = Integer.parseInt(currentJob[3]);

        if(currentJobWaitTime >= 0 && currentJobWaitTime <= 10)
            return 0;

        if(currentJobWaitTime >= 11 && currentJobWaitTime <= 300)
            return 1;

        if(currentJobWaitTime >= 301 && currentJobWaitTime <= 1800)
            return 2;

        if(currentJobWaitTime >= 1801 && currentJobWaitTime <= 43200)
            return 3;

        if(currentJobWaitTime >= 43201 && currentJobWaitTime <= Integer.MAX_VALUE)
            return 4;

        return -1;

    }

    /**
     * A server is considered to be immediately available if it is able to schedule a job immediately, which can be in
     * two different states Idle == 2 and Active == 3. This function must be used with hasSufficientResources() since
     * an active server can have insufficient resources and still be immediately be available.
     */
    public boolean isServerImmediatelyAvailable(ArrayList<String> server) {

        int serverState = Integer.parseInt(server.get(2));

        if(serverState == 2 || serverState == 3)
            return true;

        return false;

    }

    /**
     * A server has sufficient resources to run a job if the number of cores, memory and disk space are equal or greater
     * than the job's required resources.
     */
    public boolean hasSufficientResources(ArrayList<String> server, String[] currentJob) {

        int noRequiredCores = Integer.parseInt(currentJob[4]);
        int noRequiredMemory = Integer.parseInt(currentJob[5]);
        int noRequiredDiskSpace = Integer.parseInt(currentJob[6]);
        int serverCores = Integer.parseInt(server.get(4));
        int serverMemory = Integer.parseInt(server.get(5));
        int serverDiskSpace = Integer.parseInt(server.get(6));

        if(serverCores >= noRequiredCores && serverMemory >= noRequiredMemory && serverDiskSpace >= noRequiredDiskSpace)
            return true;

        return false;

    }

    /**
     * @return (Always Positive) the difference between the number of cores a server has and the number of cores the
     * job requires.
     */
    public int calculateFitnessValue(ArrayList<String> server, String[] currentJob) {

        int noRequiredCores = Integer.parseInt(currentJob[4]);
        int serverCores = Integer.parseInt(server.get(4));

        return serverCores - noRequiredCores;

    }

    // Client Command Methods

    /**
     * Resource Information Request
     *  RESC All - The informaton of all servers, in the system, regardless of their state.
     */
    public void RESCAll() {

        allServerInfo = new ArrayList<>(); // Delete old information for new data
        sendCommandNoLog("RESC All"); // Expected Response is "DATA"

        String temp = sendCommandNoLog("OK");
        while(!temp.equals(".")) {

            // Store data for each server into array
            ArrayList<String> server = new ArrayList<>();
            String[] tempServer = temp.split(" ");
            for(String serverDetail: tempServer) {
                server.add(serverDetail);
            }

            // Add server to list
            allServerInfo.add(server);

            // Get next server
            temp = sendCommandNoLog("OK");

        }

    }

    /**
     * Can be called at any time generally after a RESCAll() or RESCAvail() call to sort the new data in the list
     */
    public ArrayList<ArrayList<String>> sortAllServerInfo(ArrayList<ArrayList<String>> serverList) {

        ArrayList<ArrayList<String>> temp = new ArrayList<>();

        for(int i = 0; i < sortOrder.size(); i++) {

            for(int j = 0; j < serverList.size(); j++) {

                if(sortOrder.get(i).get(0).equals(serverList.get(j).get(0)))
                    temp.add(serverList.get(j));

            }

        }

        return temp;

    }

    /**
     * Is Run once at the start of ClientScheduler() after the RESCAll command has been called.
     * Since the the coreCount of a server can change if busy, we find the order to sort the server list once
     * before any job is scheduled.
     */
    public void findAllServerInfoSortOrder() {

        for(int i = 0; i < allServerInfo.size(); i++) {

            if(!isServerTypeInList(allServerInfo.get(i).get(0))) {

                String serverType = allServerInfo.get(i).get(0);
                String coreCount = allServerInfo.get(i).get(4);

                addServerType(serverType, coreCount, findNoServerWithType(serverType));
            }

        }

    }

    // Find number of servers with a specific type
    public String findNoServerWithType(String serverType) {

        int noServer = 0;

        for(ArrayList<String> server: allServerInfo){

            if(server.get(0).equals(serverType)) {
                noServer++;
            }

        }

        return Integer.toString(noServer);

    }

    // Helper method for findAllServerInfoSortOrder()
    public int addServerType(String serverType, String coreCount, String noServers) {

        ArrayList<String> temp = new ArrayList<>();
        temp.add(serverType);
        temp.add(coreCount);
        temp.add(noServers);

        for(int i = 0; i < sortOrder.size(); i++) {
            if(Integer.parseInt(coreCount) < Integer.parseInt(sortOrder.get(i).get(1))) {
                sortOrder.add(i, temp);
                return 0;
            }
        }

        sortOrder.add(temp);
        return 0;

    }

    // Helper method for findAllServerInfoSortOrder()
    public boolean isServerTypeInList(String otherServerType) {

        for(ArrayList<String> server: sortOrder) {

            if(server.get(0).equals(otherServerType))
                return true;

        }

        return false;

    }

    public String sendCommand(String argument){

        try {

            // Send the command to the server
            if(verbose)
                System.out.println("SENT: " +argument);
            out.write((argument+"\n").getBytes());

            // Read and return response from the server.
            String serverResponse = in.readLine();
            if(verbose)
                System.out.println("RCVD: "+serverResponse);
            return serverResponse;

        } catch(IOException i) { System.out.println(i); }

        return "ERR: No Response from Server.";

    }
    public String sendCommandNoLog(String argument) {

        try {

            // Send the command to the server
            out.write((argument+"\n").getBytes());

            // Return response from the server.
            return in.readLine();

        } catch(IOException i) { System.out.println(i); }

        return "ERR: No Response from Server.";

    }

    public static void clientUsage() {

        System.out.println("ds-sim COMP335@MQ, S1-27Apr, 2019");
        System.out.println("Usage:");
        System.out.println("    java Client [-h] [-v] [-m] [-a algo_name]");

    }

}
