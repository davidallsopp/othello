/*
  File: OthPanel.java

  Date       Author            Changes
  -- Oct 99  David N. Allsopp  Created v0.1

  07 Oct 99  "                 Debugged

  08 Oct 99  "                 Documentation; made naming more conventional. 0.2

  12 Oct 99  "                 Implemented Observer to communicate with computer player
                               instead of old tryComputerMove method. 
*/

import java.awt.*; 
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

/**
  * Maintains an othello board and draws the board as a JPanel.  Receives moves from human
  * and computer players.
  *
  * @(#)OthPanel.java 0.1 99/10/08
  * @author David N. Allsopp
  * @version 0.1 1999 October 08
  */


/**Class providing an Othello board, with checking of legal moves etc
This class, which extends JPanel, can then be wrapped in a JFrame or
other container.  This class implements the Observer interface so it
can receive moves from the computer player, which is an Observable
object. */

public class OthPanel extends JPanel implements Observer{

    OthPanel(){
	initPanel();
    }

    private final int BLACK=1,WHITE=-1;
    private final int LEGAL=1,ILLEGAL=0;

    private boolean showLegal=true;
    /** Array representing the board state.  Needs to be read by other classes, but should not
	be written to except by this class.*/
    public int boardArray[][] = new int[10][10]; // needs to be accessible to othPlayer
    private int legalArrayWhite[][] = new int[10][10];
    private int legalArrayBlack[][] = new int[10][10];
    // 8x8 boards, but leave extra space round outside to simplify loop tests
    private int whoseMove;
    private boolean isComputerBlack=false;
    private boolean isComputerWhite=false;
    private boolean locked=false; // locks board when a thread is looking for a move
    // so we know not to spawn new threads etc

    StatusBar sbar = new StatusBar(); // see inner class in this class


    /* ---------------------------------------------------------------------- */
    /* method to update the game graphics when required by the runtime system */
    /* ---------------------------------------------------------------------- */

    public void paintComponent(Graphics g) {    // gets called by the runtime library

	g.setColor(Color.lightGray);
	g.clearRect(0,0,260,260);

	// draw array of squares

	for(int x=0;x<256;x+=32){
	    for(int y=0;y<256;y+=32){
		g.draw3DRect(x,y,31,31,false); 
	    }
	}

	// draw pieces

	for(int x=1;x<9;x++){
	    for(int y=1;y<9;y++){
		switch(boardArray[x][y]){
		case BLACK : {
		    g.setColor(Color.black); 
		    g.fillOval(2+(x-1)*32,2+(y-1)*32,28,28);
		} ;break;
		case WHITE : {
		    g.setColor(Color.white); 
		    g.fillOval(2+(x-1)*32,2+(y-1)*32,28,28);
		} ;break;
		}

		// mark legal squares for the next player

		if(showLegal){
		    switch(whoseMove){
		    case BLACK : {
			g.setColor(Color.black);
			if(legalArrayBlack[x][y]==LEGAL) g.drawOval(14+(x-1)*32,14+(y-1)*32,4,4); 
		    } ;break;
		    case WHITE : {
			g.setColor(Color.white);
			if(legalArrayWhite[x][y]==LEGAL) g.drawOval(14+(x-1)*32,14+(y-1)*32,4,4); 
		    } ;break;
		    }
		}
	    }
	}
    } // end method

    /* ---------------------------------------------------------------------- */
    /*                                  MAIN - unused                         */
    /* ---------------------------------------------------------------------- */
    /** Unused main function */
    static public void main(String s[]) throws Exception {

	System.out.println("Othpanel: Main: This is a blank method.");
 
    }

    /* ---------------------------------------------------------------------- */
    /*                      method to initialise board                        */
    /* ---------------------------------------------------------------------- */
    /** Initialises the board; clears it, places initial 4 pieces, sets Black to move */
    public void initPanel(){
	whoseMove=BLACK; // black always moves first, that's the rules.

	// need to check if these initial pieces are the correct way round
	// also assuming for the moment that board coords are Across, Down
	// from the top left corner

	for(int x=0;x<10;x++){
	    for(int y=0;y<10;y++){
		boardArray[x][y]=0;
		legalArrayWhite[x][y]=ILLEGAL;
		legalArrayBlack[x][y]=ILLEGAL;
	    }
	}

	boardArray[4][4]=boardArray[5][5]=BLACK;
	boardArray[5][4]=boardArray[4][5]=WHITE; 

	legalArrayWhite[3][4]=legalArrayWhite[4][3]=
	    legalArrayWhite[5][6]=legalArrayWhite[6][5]= LEGAL;

	legalArrayBlack[3][5]=legalArrayBlack[5][3]=
	    legalArrayBlack[4][6]=legalArrayBlack[6][4]= LEGAL;

    }


    /* ---------------------------------------------------------------------- */
    /*     method to attempt a human move derived from a mouse click          */
    /* and then make move, and computer reply if appropriate                  */
    /* ---------------------------------------------------------------------- */

    /** Attempts to make a human move at column x, row y, and returns true if the move
	was made.  Returns false if the move was illegal and hence not made. */
    public boolean tryMove(int x, int y) {

	if(boardArray[x][y]!=0) return false; // square is already occupied

	// check if legal from previously constructed table of legal moves
	// and make sure that the current player isn't the computer

	switch(whoseMove){
	case BLACK: {
	    if(isComputerBlack) return false;
	    if(legalArrayBlack[x][y]!=LEGAL) return false;
	} ;break;
	case WHITE: {
	    if(isComputerWhite) return false;
	    if(legalArrayWhite[x][y]!=LEGAL) return false;
	} ;break; 
	}

	// move is legal, so update board (which will update legal table)

	makeMove(x,y,whoseMove);

	return true;  
    }


    /* ---------------------------------------------------------------------- */
    /*   receive computer's move from othPlayer, and make that move           */
    /* ---------------------------------------------------------------------- */

    /** Make computer move, using Observer/Observable interface. Currently calls
        older method tryComputerMove*/
    public void update(Observable o, Object arg){
	// if paranoid, should check that the observable is the right subtype,

	// get details from the object
	//System.out.println("Observer notified!");
	int move[]=(int[])arg;
	tryComputerMove(move[0],move[1],move[2]); 
	//System.out.println("Args: "+move[0]+","+move[1]+","+move[2]);
    }

    /** Old version - Called by computer player when it has found a move.
        This method should really be incorporated into update() */
    void tryComputerMove(int x, int y, int colour){

	// check the received move is legal
	if((colour==BLACK && legalArrayBlack[x][y]==LEGAL) ||   // paranoid check, shouldn't
	   (colour==WHITE && legalArrayWhite[x][y]==LEGAL)) {   // be necessary really
	    makeMove(x,y,colour); // make the move (which should also repaint the board)
	}
	else { // print out some debug information
	    System.out.println("Fatal error: computer attempted illegal move"); 
	    System.out.println("Colour= "+colour+" x,y= "+x+","+y+" whoseMove= "+whoseMove);

	    for(int i=0;i<10;i++){
		for(int j=0;j<10;j++){
		    if(boardArray[j][i]==BLACK)System.out.print("X");
		    else if(boardArray[j][i]==WHITE)System.out.print("O");
		    else System.out.print(".");
		}
		System.out.println(" ");
	    }
	    System.exit(0);
	}

	unlock(); // set flag so that board is free to receive the next move

	// in previous versions, see if it's still the computer's move, either because 
	// a) it has two moves in a row,
	// b) or because the user has changed the computer's colour in the middle of a move
	// c) or because computer is playing both sides:

	// Now use timer to check every so often whether the computer still needs to move,
	// which is much more straightforward and avoids horrendous thread problems

	return;
    }

    /* ---------------------------------------------------------------------- */
    /*                method to make a move and update board                  */
    /*                  NB does not check if move is legal                    */
    /* ---------------------------------------------------------------------- */

    private void makeMove(int x, int y, int whoseMove){
	int xx,yy;

	boardArray[x][y]=whoseMove;  // place new piece

	// check right (increasing x)
	xx=x; yy=y;
	while(boardArray[++xx][yy]==-whoseMove){}
	if(boardArray[xx--][yy]==whoseMove){ // have trapped some enemy pieces
	    // NB _or_ have one of your pieces adjacent! todo!
	    while(xx!=x){boardArray[xx--][yy]=whoseMove;}
	}

	// check left (decreasing x)
	xx=x; yy=y;
	while(boardArray[--xx][yy]==-whoseMove){}
	if(boardArray[xx++][yy]==whoseMove){ // have trapped some enemy pieces
	    while(xx!=x){boardArray[xx++][yy]=whoseMove;}
	}

	// check down (increasing y)
	xx=x; yy=y;
	while(boardArray[xx][++yy]==-whoseMove){}
	if(boardArray[xx][yy--]==whoseMove){ // have trapped some enemy pieces
	    while(yy!=y){boardArray[xx][yy--]=whoseMove;}
	}

	// check up (decreasing y)
	xx=x; yy=y;
	while(boardArray[xx][--yy]==-whoseMove){}
	if(boardArray[xx][yy++]==whoseMove){ // have trapped some enemy pieces
	    while(yy!=y){boardArray[xx][yy++]=whoseMove;}
	}

	// check down right 
	xx=x; yy=y;
	while(boardArray[++xx][++yy]==-whoseMove){}
	if(boardArray[xx--][yy--]==whoseMove){ // have trapped some enemy pieces
	    while(yy!=y){boardArray[xx--][yy--]=whoseMove;}
	}

	// check up left 
	xx=x; yy=y;
	while(boardArray[--xx][--yy]==-whoseMove){}
	if(boardArray[xx++][yy++]==whoseMove){ // have trapped some enemy pieces
	    while(yy!=y){boardArray[xx++][yy++]=whoseMove;}
	}

	// check down left 
	xx=x; yy=y;
	while(boardArray[--xx][++yy]==-whoseMove){}
	if(boardArray[xx++][yy--]==whoseMove){ // have trapped some enemy pieces
	    while(yy!=y){boardArray[xx++][yy--]=whoseMove;}
	}

	// check up right 
	xx=x; yy=y;
	while(boardArray[++xx][--yy]==-whoseMove){}
	if(boardArray[xx--][yy++]==whoseMove){ // have trapped some enemy pieces
	    while(yy!=y){boardArray[xx--][yy++]=whoseMove;}
	}

	updateLegalArrays();

	repaint(); 

	// NB must update legal array first so that the new legal square are shown correctly

	return;
    } // end of method makeMove

    /* ---------------------------------------------------------------------- */
    /*                 Method to update legal arrays after a move             */
    /*       and to determine whose move is next, and detect end of game      */
    /* ---------------------------------------------------------------------- */

    private void updateLegalArrays() {
	int xx,yy;
	int legalBlack=0;
	int legalWhite=0;

	// Simplest way (not the most efficient) is to check separately for 
	// black and white, and to just iterate through all the squares
	// NB a square can be legal for both players!

	// A possible more efficient way would be to check each direction in
	// a colour-independent way, as each diection can only flip pieces
	// for one or zero players.  You would have to note when the legality
	// is determined for each player so you can exit as early as possible

	// todo: also inefficient to check for both players first, and _then_
	// see whose turn it is!

	//check for black

	for(int x=1; x<9; x++){
	    for(int y=1; y<9; y++){

		// if square is full then it's illegal 
		if(boardArray[x][y]!=0){legalArrayBlack[x][y]=ILLEGAL;continue;}

		// check right (increasing x)
		xx=x; yy=y; if(boardArray[++xx][yy]==WHITE){
		    while(boardArray[++xx][yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check left (decreasing x)
		xx=x; yy=y; if(boardArray[--xx][yy]==WHITE){
		    while(boardArray[--xx][yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check down (increasing y)
		xx=x; yy=y; if(boardArray[xx][++yy]==WHITE){
		    while(boardArray[xx][++yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check up (decreasing y)
		xx=x; yy=y; if(boardArray[xx][--yy]==WHITE){
		    while(boardArray[xx][--yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check down right 
		xx=x; yy=y; if(boardArray[++xx][++yy]==WHITE){
		    while(boardArray[++xx][++yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check up left
		xx=x; yy=y; if(boardArray[--xx][--yy]==WHITE){
		    while(boardArray[--xx][--yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check down left
		xx=x; yy=y; if(boardArray[--xx][++yy]==WHITE){
		    while(boardArray[--xx][++yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		// check up right
		xx=x; yy=y; if(boardArray[++xx][--yy]==WHITE){
		    while(boardArray[++xx][--yy]==WHITE){}
		    if(boardArray[xx][yy]==BLACK){legalArrayBlack[x][y]=LEGAL;
                    legalBlack++;continue;}
		}
		legalArrayBlack[x][y]=ILLEGAL;

	    }
	}

	//check for white

	for(int x=1; x<9; x++){
	    for(int y=1; y<9; y++){

		// if square is full then it's illegal 
		if(boardArray[x][y]!=0){legalArrayWhite[x][y]=ILLEGAL;continue;}

		// check right (increasing x)
		xx=x; yy=y; if(boardArray[++xx][yy]==BLACK){
		    while(boardArray[++xx][yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check left (decreasing x)
		xx=x; yy=y; if(boardArray[--xx][yy]==BLACK){
		    while(boardArray[--xx][yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check down (increasing y)
		xx=x; yy=y; if(boardArray[xx][++yy]==BLACK){
		    while(boardArray[xx][++yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check up (decreasing y)
		xx=x; yy=y; if(boardArray[xx][--yy]==BLACK){
		    while(boardArray[xx][--yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check down right 
		xx=x; yy=y; if(boardArray[++xx][++yy]==BLACK){
		    while(boardArray[++xx][++yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check up left
		xx=x; yy=y; if(boardArray[--xx][--yy]==BLACK){
		    while(boardArray[--xx][--yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check down left
		xx=x; yy=y; if(boardArray[--xx][++yy]==BLACK){
		    while(boardArray[--xx][++yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		// check up right
		xx=x; yy=y; if(boardArray[++xx][--yy]==BLACK){
		    while(boardArray[++xx][--yy]==BLACK){}
		    if(boardArray[xx][yy]==WHITE){legalArrayWhite[x][y]=LEGAL;
                    legalWhite++;continue;}
		}
		legalArrayWhite[x][y]=ILLEGAL;

	    }
	}

	whoseMove=-whoseMove; //change player
	if(whoseMove==BLACK) sbar.setStatusBarText("Black to move.");
	else sbar.setStatusBarText("White to move.");

	if(legalBlack+legalWhite<1){
	    System.out.println("Neither player can move: End of game."); //debug
	    sbar.setStatusBarText("End of game."+getResult());
	    whoseMove=0; // needed to prevent computer trying to move again?
	    // lock(); // shouldn't be necessary
	}
	else if(legalBlack==0 && whoseMove==BLACK){
	    System.out.println("Black can't move: White to move."); //debug
	    sbar.setStatusBarText("Black can't move: White to move.");
	    whoseMove=WHITE;
	}
	else if(legalWhite==0 && whoseMove==WHITE){
	    System.out.println("White can't move: Black to move."); //debug
	    sbar.setStatusBarText("White can't move: Black to move.");
	    whoseMove=BLACK;
	}

	return;
    } // end of method updateLegalArrays

    /* ---------------------------------------------------------------------- */
    /*                  toggle showing of legal moves                         */
    /* ---------------------------------------------------------------------- */

    /** Controls whether the legal moves available to a player are drawn on the board */
    public void setShowLegal(boolean b){showLegal=b; repaint();}


    /* ---------------------------------------------------------------------- */
    /*      count pieces at end of game and return score as string            */
    /* ---------------------------------------------------------------------- */

    private String getResult(){

	int black=0;
	int white=0;

	for(int x=1; x<9; x++){
	    for(int y=1; y<9; y++){
		if(boardArray[x][y]==WHITE) white++;
		else if(boardArray[x][y]==BLACK) black++;
	    }
	}

	if(white==black) return " Game drawn.";
	else if(white>black) return (" White wins "+white+"-"+black+".");
	else return (" Black wins "+black+"-"+white+".");

    }

    /* ---------------------------------------------------------------------- */
    /*     set/get state of computer play, and current player, etc            */
    /* ---------------------------------------------------------------------- */

    public void setComputerBlack(boolean b){isComputerBlack=b;}
    public void setComputerWhite(boolean b){isComputerWhite=b;}
    public boolean getIsComputerBlack(){return isComputerBlack;}
    public boolean getIsComputerWhite(){return isComputerWhite;}
    /** Returns the current player; Black=1, White=-1. May return zero after the current
	game is ended */
    public int getWhoseMove(){return whoseMove;}
    public boolean locked(){return locked;}
    /** Sets a flag which should be checked before attempting to make a human move. 
        The board should be locked when the computer is thinking and unlocked when 
        it has finished its move.*/
    public void lock(){locked=true;}
    public void unlock(){locked=false;}

    /* ---------------------------------------------------------------------- */
    /*                        Inner member class : status bar                 */
    /* ---------------------------------------------------------------------- */

    // needs to be instantiated in OthPanel class so that it is visible to the
    // JPanel as well as to the enclosing JFrame

    class StatusBar extends JLabel{

	StatusBar(){ //constructor
	    super();
	    setBorder(BorderFactory.createEtchedBorder()); 
	}

	public void setStatusBarText(String s){
	    setText(" "+s); //leading space looks nicer
	}
    }


}   // end of class OthPanel

