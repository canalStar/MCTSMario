package itu.ejuuragr.UCT;

import itu.ejuuragr.MCTSAgent;
import itu.ejuuragr.MCTSTools;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import competition.cig.robinbaumgarten.astar.LevelScene;
import competition.cig.robinbaumgarten.astar.level.Level;

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.engine.MarioComponent;
import ch.idsia.mario.environments.Environment;

/**
 * This Agent for the Mario AI Benchmark is based on the UTC MCTS algorithm
 * (Upper Confidence Bound for Trees Monte Carlo Tree Search).
 * 
 * @author Emil & Rasmus
 *
 */
public class SimpleMCTS extends KeyAdapter implements MCTSAgent<UCTNode> {
	
	protected static int TIME_PER_TICK = 39; // milliseconds
	public static int RANDOM_SAMPLES_LIMIT = 4;
	protected static final double cp = 1.5/8;// 1.0/Math.sqrt(2); // 1.5/8;
	
	protected int maxDepth = 0;
	
	private float lastX, lastY; //For simulation error correction
	private int lastMode = 2;
	
	private String name = "BasicMCTS";
	protected UCTNode root;
	
	private HashMap<String, Integer> heuristic = new HashMap<String, Integer>();
	private LinkedList<Integer> nodeCounts = new LinkedList<Integer>();

	@Override
	public void reset() {
		MCTSTools.print("Agent Reset");
		initRoot();
	}
	
	@Override
	public boolean[] getAction(Environment obs) {		
		int m = obs.getMarioMode();
		if (m != lastMode)
			MCTSTools.print("Mode changed: " + m);
		lastMode = m;
		return search(obs);
	}

	@Override
	public AGENT_TYPE getType() {
		return Agent.AGENT_TYPE.AI;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}
	
	private void fixSimulationErrors(Environment obs)
	{
		if (root != null && root.state != null)
		{
			float[] marioPos = obs.getMarioFloatPos();
			if (root.state.mario.x != marioPos[0] || root.state.mario.y != marioPos[1])
			{
				//if (marioPos[0] == lastX && marioPos[1] == lastY)
				//	System.out.println("Game won!?");
				if (root.state.mario.x == marioPos[0])
					MCTSTools.print(String.format("CORRECTED POSITIONS! y: %s -> %s", root.state.mario.y, marioPos[1]));
				else if	(root.state.mario.y == marioPos[1])
					MCTSTools.print(String.format("CORRECTED POSITIONS! x: %s -> %s", root.state.mario.x, marioPos[0]));
				else
					MCTSTools.print(String.format("CORRECTED POSITIONS! x: %s -> %s, y: %s -> %s", root.state.mario.x, marioPos[0], root.state.mario.y, marioPos[1]));
			
				// Set the simulator mario to the real coordinates (x and y) and estimated speeds (xa and ya)
				root.state.mario.x = marioPos[0];
				root.state.mario.xa = (marioPos[0] - lastX) *0.89f;
				if (Math.abs(root.state.mario.y - marioPos[1]) > 0.1f)
					root.state.mario.ya = (marioPos[1] - lastY) * 0.85f; //+ 3f;
				root.state.mario.y = marioPos[1];
			}
			if (root.state.mario.fire && obs.getMarioMode() < 2 || root.state.mario.large && obs.getMarioMode() < 1 || root.state.mario.deathTime > 0)
			{
				if (obs.getMarioMode() == 2)
				{
					root.state.mario.fire = true;
					root.state.mario.large = true;
				}
				else if (obs.getMarioMode() == 1)
				{
					root.state.mario.fire = false;
					root.state.mario.large = true;
				}
				else
				{
					root.state.mario.fire = false;
					root.state.mario.large = false;
				}
				root.state.mario.deathTime = 0;
				MCTSTools.print(String.format("CORRECTED MARIO STATE! Fire: %s Large: %s", root.state.mario.fire, root.state.mario.large));
			}
		}
		lastX = obs.getMarioFloatPos()[0];
		lastY = obs.getMarioFloatPos()[1];
	}
	
	/**
	 * Performs MCTS for the most optimal move for 39 ms and
	 * returns the action that seems to lead to the best outcome.
	 * It will reuse the tree for MAX_REUSES times and then
	 * create a brand new tree with the given Environment.
	 * 
	 * @param obs The Environment just recieved from the game.
	 * @return The action that seems to be best.
	 */
	public boolean[] search(Environment obs){
		long startTime = System.currentTimeMillis();
		long endTime = startTime + TIME_PER_TICK;
		
		fixSimulationErrors(obs);
		//Before searching
		clearRoot(obs);  //reset root, add observation information
		
		//Search
		maxDepth = 0;
		//int nodesToCreate = 1100;
		//while (nodesToCreate-- > 0){
		while(System.currentTimeMillis() < endTime){
			UCTNode v1 = treePolicy(root);
			double reward = defaultPolicy(v1);
			backup(v1,reward);
		}
		
		if(MCTSTools.DEBUG){
			nodeCounts.add(root.visited);
			int avg = 0;
			for (Integer i : nodeCounts) avg += i;
			avg /= nodeCounts.size();
			
			MCTSTools.print(String.format("Depth: %2d, at %4d nodes %3dms used (%4d nodes avg.)",maxDepth,root.visited,System.currentTimeMillis() - startTime,avg));
		}
		
		//Selecting action
		if(root.visited != 1){
			UCTNode choice = root.getBestChild(0);
			if (root.state.mario.fire != choice.state.mario.fire || root.state.mario.large != choice.state.mario.large || choice.state.mario.deathTime > root.state.mario.deathTime)

				MCTSTools.print("I'm gonna die and i know it! ("+choice.state.mario.fire+" , " + choice.state.mario.large + " , " + choice.state.mario.deathTime + ") Reward:" + choice.reward);
			
			if (MCTSTools.DEBUG)
			{
				//drawFuture(root);
				//drawEventHorizon(root);
			}
			
			root = choice;
			//addHeuristic(choice.action);
			return choice.action;
			
		}else{
			return new boolean[5];
		}
	}
	
	private void addHeuristic(boolean[] action){
		String sAction = MCTSTools.actionToXML(action);
		int lastValue = heuristic.containsKey(sAction) ? heuristic.get(sAction) : 0 ;
		heuristic.put(sAction,  lastValue  + 1);
	}
	
	private void printHeuristic(){
		int total = 0;
		for(Entry<String, Integer> entry : heuristic.entrySet()){
			System.out.println(entry.getKey() + ": "+entry.getValue());
			total += entry.getValue();
		}
		System.out.println("Total moves: "+total);
	}
	
	protected void drawFuture(UCTNode v)
	{
		ArrayList<Integer> xs = new ArrayList<Integer>();
		ArrayList<Integer> ys = new ArrayList<Integer>();
		
		while (v != null)
		{
			xs.add((int)v.state.mario.x);
			ys.add((int)v.state.mario.y);
			v = v.getBestChild(0);
			
		}
		int[] rx = new int[xs.size()];
		int[] ry = new int[xs.size()];
		for (int i = 0; i < xs.size(); i++)
		{
			rx[i] = xs.get(i);
			ry[i] = ys.get(i);
		}
		MarioComponent.BESTLINE_XS = rx;
		MarioComponent.BESTLINE_YS = ry;
	}
	
	private void collectLeafs(UCTNode v, LinkedList<UCTNode> list)
	{
		if (v.numChildren == 0)
		{
			list.add(v);
			return;
		}
		for (UCTNode c : v.children)
		{
			if (c != null)
				collectLeafs(c, list);
		}
	}
	
	private ArrayList<UCTNode> order(LinkedList<UCTNode> leafs)
	{
		LinkedList<UCTNode> queue = leafs;
		ArrayList<UCTNode> sorted = new ArrayList<UCTNode>();
		
		UCTNode current = queue.removeFirst();
		sorted.add(current);
		while (!queue.isEmpty())
		{
			double distance = 0.0;
			UCTNode closest = null;
			for (UCTNode n : queue)
			{
				double nd = distance(n,current);
				if (distance <= nd)
				{
					distance = nd;
					closest = n;
				}
			}
			sorted.add(closest);
			queue.remove(closest);
			current = closest;
		}
		return sorted;
	}
	
	private double distance(UCTNode a, UCTNode b)
	{
		double as = Math.pow(a.state.mario.x-b.state.mario.x,2);
		double bs = Math.pow(a.state.mario.y-b.state.mario.y,2);
		return Math.sqrt(as+bs);
	}
	
	protected void drawEventHorizon(UCTNode v)
	{
		LinkedList<UCTNode> leafs = new LinkedList<UCTNode>();
		collectLeafs(v, leafs);
		ArrayList<UCTNode> ordered = order(leafs);
	
		int[] rx = new int[ordered.size()];
		int[] ry = new int[ordered.size()];
		for (int i = 0; i < ordered.size();i++)
		{
			UCTNode c = ordered.get(i);
			rx[i] = (int)c.state.mario.x;
			ry[i] = (int)c.state.mario.y;
		}
		MarioComponent.PLAN_XS = rx;
		MarioComponent.PLAN_YS = ry;
	}
	
	private void logState(){
		MarioComponent.SAVE_NEXT_FRAME = true;
		root.outputTree("Tree.xml");
		//printHeuristic();
		printActions();
	}
	
	private void printActions(){
		System.out.println("Actions by index:");
		for(int i = 0; i < MCTSTools.CHILDREN; i++){
			System.out.println(i + ": "+MCTSTools.actionToXML(MCTSTools.indexToAction(i)));
		}
	}
	
	public void keyPressed (KeyEvent e)
    {
		if (e.getKeyCode() == KeyEvent.VK_SPACE)
        {
			System.out.println("Saving state");
			logState();
        }
		else if (e.getKeyCode() == KeyEvent.VK_UP)
		{
			TIME_PER_TICK += 10;
			System.out.println("Time pr tick: " + TIME_PER_TICK);
		}
		else if (e.getKeyCode() == KeyEvent.VK_DOWN) 
		{
			TIME_PER_TICK -= 10;
			while (TIME_PER_TICK < 0) TIME_PER_TICK += 10;
			System.out.println("Time pr tick: " + TIME_PER_TICK);
		}
    }
	
	public UCTNode createRoot(LevelScene state){
		return new UCTNode(state, null, null);
	}
	
	/**
	 * Creates a new root node (MCTreeNode) with the data in
	 * the given state (Environment).
	 * 
	 * @param obs The current state of the game to simulate from.
	 */
	private void initRoot(){
		LevelScene	l = new LevelScene();
		l.init();
		l.level = new Level(1500,15);
		

		root = createRoot(l);
		root.state.tick();
	}
	
	/**
	 * Clear the (newly selected) root node of all children 
	 * and get it ready for searching 
	 * @param obs
	 */
	protected void clearRoot(Environment obs)
	{
		root.reset();
		root.state.setEnemies(obs.getEnemiesFloatPos());
		root.state.setLevelScene(obs.getLevelSceneObservationZ(0));
	}

	/**
	 * From the given MCTreeNode it will go up the tree and add
	 * the reward value to all parents.
	 * 
	 * @param v The MCTreeNode that has recieved the reward.
	 * @param reward The reward for the given node (how good it is).
	 */
	public void backup(UCTNode v, double reward) {
		int depth = 1;
		v = v.parent;
		while(v != null){
			v.visited++;
			v.reward += reward;
			v = v.parent;
			depth++;
		}
		if(depth > maxDepth) maxDepth = depth;
	}

	/**
	 * Performs a random simulation on the current node to see how
	 * viable it is.
	 * 
	 * @param node The node to simulate random actions on.
	 * @return The final reward for the node after the simulations.
	 */
	public double defaultPolicy(UCTNode node) {
		//return node.calculateReward(node.state);

		return node.advanceXandReward(RANDOM_SAMPLES_LIMIT);
	}

	/**
	 * Goes through the tree and creates a new leaf somewhere choosing
	 * the path at each step by the bestChild-method (which can either
	 * be exploration or exploitation).
	 * 
	 * @param v The root of the tree.
	 * @return The new leaf.
	 */
	public UCTNode treePolicy(UCTNode v) { // may not be right
		while(true){
			if(!v.isExpanded()){
				return v.expand();
			}else{
				v = v.getBestChild(cp);
			}
		}
	}
}
