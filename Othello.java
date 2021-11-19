/*
  File: Othello.java

  Date       Author            Changes
  -- Oct 99  David N. Allsopp  Created v0.1

  08 Oct 99  "                 Put computer player into separate thread launched by timer. 0.12

  09 Oct 99  "                 Documentation; made naming more conventional. 0.13

  10 Oct 99  "                 Use of setup() rather than accessing variables directly in 
                               OthPlayerThread. 0.13.1

  12 Oct 99  "                 Changes to allow the computer player and board to communicate
                               using the Observer/Observable interface. 0.14
			       including setting up OthPlayerThread differently.

*/

import java.awt.*; 
import java.awt.event.*;
import javax.swing.*;
import java.io.*;

/**
  * This is the main class for an Othello board game. The board is maintained and drawn by
  * an OthPanel and a computer opponent is provided by OthPlayerThread.
  *
  * @(#)Othello.java 0.14 99/10/12
  * @author David N. Allsopp
  * @version 0.14 1999 October 12
  * @see OthPanel.java
  * @see OthPlayerThread.java
  */


public class Othello{

    static final int BLACK=1,WHITE=-1;

    static int searchLevel=5; // initial search depth, adjustable from menu
    static boolean solving=true; // does computer use endgame solving?
    static final OthPanel board = new OthPanel(); 
    static Thread thr = new Thread(); // computer opponent in another thread
    static OthPlayerThread opt = new OthPlayerThread();

    /* ---------------------------------------------------------------------- */
    /*                                  MAIN                                  */
    /* ---------------------------------------------------------------------- */

    static public void main(String s[]) throws Exception {
            
	board.initPanel(); 
	// clear board, add first four counters, set first player

	board.sbar.setStatusBarText("Othello status bar.");
	  
	final JFrame frame = new JFrame("Othello"); // Top-level container for everything

	// timer which checks every 1/20 sec whether computer should move. This greatly
	// simplifies the situations where there computer moves twice in a row, or
	// is playing both sides, or the user changes the computer's colour halfway through
	// a move, etc.
	Timer timer= new Timer(50,new ActionListener(){     
		public void actionPerformed(ActionEvent e){
		    allowComputerToMove();
		}});


	// Declare and attach all the menus and menu items
	JMenuBar mb = new JMenuBar();
	JMenu m1 = new JMenu("Game");
	JMenu m2 = new JMenu("Options");
	JMenu m4 = new JMenu("Level");
	JMenu m3 = new JMenu("Help");
	m1.setToolTipText("Main menu");
	m2.setToolTipText("Board and game options");
	m3.setToolTipText("Game rules and program help");
	m4.setToolTipText("Level of computer play");

	final JCheckBoxMenuItem m2mi1 = new JCheckBoxMenuItem("Show legal moves");
	m2mi1.setState(true);
	m2.add(m2mi1); m2.addSeparator();

	final JCheckBoxMenuItem m4mi1 = new JCheckBoxMenuItem("Endgame solving");
	m4mi1.setState(true);

	final JRadioButtonMenuItem human = new JRadioButtonMenuItem("2 Humans play");
	final JRadioButtonMenuItem cwhite = new JRadioButtonMenuItem("Computer plays white");
	final JRadioButtonMenuItem cblack = new JRadioButtonMenuItem("Computer plays black");
	final JRadioButtonMenuItem cboth = new JRadioButtonMenuItem("Computer plays both");
     
	ButtonGroup group = new ButtonGroup();
	group.add(human); group.add(cwhite); group.add(cblack); group.add(cboth);
	human.setSelected(true);
	//cboth.setEnabled(false);

	ButtonGroup levels = new ButtonGroup();

	for(int i=2;i<11;i++){
	    final JRadioButtonMenuItem item = new JRadioButtonMenuItem(i+"");
	    item.setActionCommand(i+"");
	    levels.add(item);
	    m4.add(item);
	    item.addActionListener(new ActionListener(){
		    public void actionPerformed(ActionEvent e){
			String numStr = e.getActionCommand();
			searchLevel = Integer.parseInt(numStr);
		    }});
	    if(i==5)item.setSelected(true);
	}
	m4.addSeparator();
	m4.add(m4mi1);

	// four handlers for setting which side(s) the computer is playing on.

	human.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e){ 
		    board.setComputerBlack(false); board.setComputerWhite(false);
		}});

	cwhite.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e){ 
		    board.setComputerBlack(false); board.setComputerWhite(true);  
		    allowComputerToMove();  
		}});

	cblack.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e){ 
		    board.setComputerBlack(true); board.setComputerWhite(false);
		    allowComputerToMove();              
		}});

	cboth.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e){ 
		    board.setComputerBlack(true); board.setComputerWhite(true);
		    allowComputerToMove();   
		}});

	// HELP menu
   
	m2.add(human); m2.add(cwhite); m2.add(cblack); m2.add(cboth);
	//JMenuItem m3mi1=new JMenuItem("About...");
	//JMenuItem m3mi2=new JMenuItem("Game rules");
	JMenuItem m3mi3=new JMenuItem("Game help");
	//m3.add(m3mi1); 
	//m3.add(m3mi2); 
	m3.add(m3mi3);


	File helpFile= new File("help.html");
	if(helpFile.exists()){
	    //System.out.println("Found help file: "+helpFile.getAbsolutePath() );
	final JFrame jf = new JFrame("Othello help");
	JEditorPane jep=null; 
	java.net.URL helpURL=null;
	try{
	    helpURL=helpFile.toURL();
	}
	catch(java.net.MalformedURLException mue){
	    System.out.println("Error: "+mue);
	}

	try{
	jep=new JEditorPane(helpURL);
	jep.setEditable(false);
	JScrollPane jsp = new JScrollPane(jep); //wrap editorpane in scrollpane
	jsp.setPreferredSize(new Dimension(256,256));
	jf.getContentPane().add(jsp); // and wrap scrollpane in JFrame
	jf.pack();
	}
	catch(java.io.IOException e){
	    System.out.println("Error: "+e);
	    }

	m3mi3.addActionListener(new ActionListener(){ // open help file
		public void actionPerformed(ActionEvent e){ 
		    jf.setVisible(true);
		}});
	}
	else{
	    m3.setEnabled(false); // disable menu if can't find help file
	}

        // GAME menu

	JMenuItem mi1=new JMenuItem("New game");
	JMenuItem mi2=new JMenuItem("Quit");

	mi1.addActionListener( new ActionListener(){
		public void actionPerformed(ActionEvent e)
		{
		    opt.stopit();  // stop running thread
		    board.unlock();
		    board.initPanel(); // reset board
		    board.sbar.setStatusBarText("New game: Black to move.");
		    frame.repaint();
		    allowComputerToMove();  
		}});

	mi2.addActionListener( new ActionListener(){
		public void actionPerformed(ActionEvent e)
		{
		    System.exit(0);
		}});

	m2mi1.addActionListener( new ActionListener(){ // handle check box menu item
		public void actionPerformed(ActionEvent e)
		{
		    board.setShowLegal(m2mi1.getState()); 
		}});

	m4mi1.addActionListener( new ActionListener(){ // handle check box menu item
		public void actionPerformed(ActionEvent e)
		{
		    solving=m4mi1.getState();
		}});


	m1.add(mi1); m1.add(mi2);
	mb.add(m1);mb.add(m2);mb.add(m4);mb.add(m3);


        frame.setJMenuBar(mb);
        frame.getContentPane().setLayout(new BorderLayout(1,1));
        board.setSize(new Dimension(256,256)); //needed for proper frame repaint
        board.setPreferredSize(new Dimension(256,256)); // seems to be needed for pack()
        frame.getContentPane().add(board, "Center");

        board.sbar.setMaximumSize(board.getPreferredSize());
        frame.getContentPane().add(board.sbar, "South");

        frame.pack();
        frame.setResizable(false);
        frame.setVisible(true);

        board.addMouseListener(new MouseAdapter() {
		public void mousePressed(MouseEvent me) {
		    // see if it's a human's turn, and ignore if not
		    if(!board.locked()){
			if((board.getWhoseMove()==BLACK && !board.getIsComputerBlack()) || 
			   (board.getWhoseMove()==WHITE && !board.getIsComputerWhite())) {
			    int mx=me.getX(); int my=me.getY();
			    int x=1+mx/32; int y=1+my/32;
			    if(board.tryMove(x,y)) frame.repaint();} 
                             // try to make a human move
          
		        // May now be a computer move(s) -
		        // see if it is, and if so tell the computer player to  
			//get on with it/them
			allowComputerToMove();          
		    }
		}
	    });

        frame.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) 
		{System.exit(0);}
	    });

        timer.start();

    }


    /* ---------------------------------------------------------------------- */
    /* Attempt to make computer move, if necessary. This method is called by  */
    /*  various event handlers and also from an instance of OthPanel          */
    /* ---------------------------------------------------------------------- */


    private static void allowComputerToMove(){

	//System.out.println("Entered allowComputerToMove()");

	if(!board.locked()){
	    if((board.getWhoseMove()==BLACK && board.getIsComputerBlack()) || 
	       (board.getWhoseMove()==WHITE && board.getIsComputerWhite())) {
		board.sbar.setStatusBarText("Thinking..."); 
		opt = new OthPlayerThread(); // reusing the same object causes problems -
		// should have a look and work out why...todo
		thr = new Thread(opt); // computer opponent in another thread
		opt.addObserver(board); // register observer/observable interface
		opt.setup(board.boardArray,board.getWhoseMove(),searchLevel,solving); 
                  // initialise computer player
		board.lock(); //prevent board changes until thread has finished
		thr.start(); // set new thread running
	    }
	}
    }  
 
    /* ---------------------------------------------------------------------- */


}   // end of class Othello


