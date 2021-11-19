/*
  File: OthPlayerThread.java

  Date       Author            Changes
  -- Oct 99  David N. Allsopp  Created v0.1

  07 Oct 99  "                 Debugged, provision for iterative deepening (not enabled)

  08 Oct 99  "                 Documentation; made naming more conventional. 0.2
                               Began support for ternary-index board representation 
                               (see explanation at the end of this file)

  10 Oct 99  "                 First working version with indices. Initially about 3 times
                               slower than previous, but plays same positions (good!) 0.3
                               - Made offset tables static - very noticeable speed increase.
                               - Tidied up moving make/undo - small speed increase (13%)
                               - Used iMakeNextMove - now slightly faster than v0.2!
			       - Compared flipping pieces with looking up which to flip
			         and there was no significant difference.
                               - used separate code for black and white to remove one
                                 of the lookup table indices - 18% speedup

  11 Oct 99  "                 Pre-created the undoData objects for the search tree. 0.4
                               This gives speedup of 44% ! 
			       (example timing 40 secs, compared to 102 secs for version 0.2)
			       - added setup() method to replace accessing setup variables,
			         and added some checking of setup parameters.
			       - Combined flipping pieces with code from i_legal to save some
			         wasted effort - another 23% speedup (now 31 secs cf 40 secs)
			       - removed some obsolete methods
			       - *** fixed awful, awful bug; assessing for incorrect player
			         on some levels - thought I'd got that sorted a long time ago!
			       - Added simple form of endgame searching - a bit crude at 
			         present.

  12 Oct 99  "                 Changed to Runnable interface so we can subclass Observable
                               to communicate with the board (OthPanel) rather than the 
                               previous ad hoc method. 0.41.  Changes to parameters of setup
			       method to remove need for an OthPanel instance. This makes
			       the class more flexible as it can be called with just a plain
			       array for the board, and an Observer registered.

  14 Oct 99  "                 Minor improvements to assess() - removed count of pieces,
                               relying just on mobility, corners, and discouraging C,X squares

*/

/*

  Things to do:
  0) shallow search before WLD solving, to get best/fastest move
  1) Saving of solved games to create database, or find one on the 'net.
  2) Improve the evaluation function by using indices and mobility
  3) See if move ordering or iterative deepening help after that
  4) Endgame solver improvements
  5) Opening book (after 2)
  6) Time scheduling rather than fixed depth
  7) Negascout? or other narrow-window algorithms
  8) Killer move heuristic
  9) Hash table 
  10) Using 9) (or otherwise) - thinking on opponent's time 
  11) progress bar or percentage for the GUI
*/

/**
  * This is a computer othello opponent which runs in a separate
  * thread from the GUI.  It is run in response to events in the main
  * Othello class, and sends its move via the Observer/Observable
  * interface It provides a best move, given a board position and
  * player to move and depth to search, using a recursive negamax
  * alpha-beta pruning algorithm.  It should not be called if there
  * are no valid moves for the player.
  *
  * @(#)OthPlayerThread.java 0.41 99/10/12
  * @author David N. Allsopp
  * @version 0.41 1999 October 12 
*/


import java.util.*;
import java.io.*;

public class OthPlayerThread extends Observable implements Runnable{


    int[][] aboard;
    int rootPlayer=0; 
    int depth=2;
    boolean setupYet=false;
    int pieces=0; // how many pieces on the board?
    boolean solving=true;
    boolean savingSolved=true;
    int solveDepth=17;  // WLD solve at 16 left, fully solve at 15 left


    /* --------------------------------thread control----------------------------- */
    volatile Thread signal;

    /** Signals to the thread that it should stop and die.  The thread may not stop
	immediately, but it will cease to take any part in the game */
    public void stopit() {
	signal= null;
    }

    /* -------------------------------- setup ------------------------------------ */

    /** Provide setup information before starting. Need a 10x10 array
containing the board, the search depth required (in half-moves), the
player, and whether endgame solving should be used. High search depths
may be unuseably slow, depending on your hardware.  Future versions
may include variable-depth search with time constraints.  This method
makes some checks on the validity of the parameters, and may refuse to
allow the thread to be started if some checks are failed. */

    public void setup(int[][] board,int player,int depth, boolean solving){
	setupYet=true;
	this.depth=depth;
	this.solving=solving;
	aboard=board;
	if(depth<1 || depth>20){
	    this.depth=5; 
	    System.out.println("Error: Illegal search depth. Using default of 5.");
	}
	rootPlayer=player;
	if(rootPlayer!=BLACK && rootPlayer!=WHITE){
	    System.out.println("Error: Illegal value for player. Can't make computer move.");
	    setupYet=false; // Invalidate setup - won't be able to start thread.
	}

	// verify board (just checks that all squares are black, white
	// or empty):
        // If you want to be really paranoid you could check that the outermost
	// squares are all empty, but that's not implemented here.

    outer: for(int x=0;x<10;x++){
	for(int y=0; y<10; y++){
	    if(board[x][y]!=BLACK && 
	       board[x][y]!=WHITE && 
	       board[x][y]!=0){
		setupYet=false;  // Invalidate setup - won't be able to start thread.
		System.out.println("Error: Illegal board contents. Can't make computer move.");
		break outer;
	    }
	}
    }

	for(int x=1;x<9;x++){
	    for(int y=1; y<9; y++){
		if(board[x][y]!=0) pieces++;
	    }
	}

    }

   /* ------------------------------ tables and constants ----------------------- */

    static final int mobilityTable[]=new int[6561];

    static final int BLACK=1,WHITE=-1;
    static final int MOBIL_WEIGHT=2,POTEN_WEIGHT=1; 
    // way of weighting contribution from mobility and potential mobility

    static final byte legalTable[][]=new byte[2][6561]; 
    // lookup table to help find legal moves
    // entry for each colour and index

    static final byte flipsTable[][][]=new byte[2][6561][8];
    // lookup table to find what pieces are flipped over in a single line
    // when a move is made with particular _colour_, on line with given _index_
    // at a given _location_ along the line.

    static final int placeOffsetTable[][]=new int[][] 
    {{2187,729,243,81,27,9,3,1},{4374,1458,486,162,54,18,6,2}};
    static final int flipOffsetTable[][] =new int[][] 
    {{-2187,-729,-243,-81,-27,-9,-3,-1},{2187,729,243,81,27,9,3,1}};
    static final int placeOffsetTableB[]=new int[] {2187,729,243,81,27,9,3,1};
    static final int placeOffsetTableW[]=new int[] {4374,1458,486,162,54,18,6,2};
    static final int flipOffsetTableB[]=new int[] {-2187,-729,-243,-81,-27,-9,-3,-1};
    static final int flipOffsetTableW[]=new int[] {2187,729,243,81,27,9,3,1};
    // lookup tables giving the change in a board index when a piece is placed or
    // flipped at a given position

    static{ 
	initTables(); // initialise legalTable and mobilityTable 
    }

    Board iboard=new Board(); 
    
    undoData udarray[]=new undoData[30]; 
    // These objects are declared here (so they are accessible to the
    // run() and negamax() methods.  They are created in the run
    // method, in advance, so that they don't need to be created and
    // re-created on the fly in negamax(). This gives a large speed
    // improvement - over 40% less time taken.



    /* --------------------------------------------------------------------------- */
    /** Inner class encapsulating data needed to undo a move on the board */

    class undoData{
	int moveX,moveY;    // the last move made 
	int byWhom; // by which player? - NB one player can make several moves in
	// a row if the other player can't move. Could actually just read
	// the colour from the board, since we have coords - todo.
	int numFlips;       // how many pieces were flipped by that move
	int flipX[]=new int[19]; // their coordinates. 19 is the theoretical maximum
	int flipY[]=new int[19]; // no. of pieces that can be flipped in one move.
    }


    /* --------------------------------------------------------------------------- */
    /** Representation of game board.  As well as 10x10 array, 
	contains 46 indices which are ternary representations of the contents
	of each row, column or diagonal.  This allows immediate lookup of the
	legal moves on that row, column or diagonal, for instance */
  
    class Board{
	int array[][]=new int[10][10];
	int row[]=new int[8];       /* row ----- */
	int column[]=new int[8];    /* column ||||| */  
	int diag1[]=new int[15];    /* diag1 */   
	int diag2[]=new int[15];    /* diag2 \\\\\ */   
    }



    /* --------------------------- main method - runnable ------------------------ */
    /** Method called by another class to start searching a given board            
	for the best move for a given player, searching a fixed depth (at present).
	This method forms the root of the search tree.                          
        The setup() method must be called before calling this method with start().
        The board should also be locked by calling a method in OthPanel.*/

    public void run(){
	if(!setupYet){
	    System.out.println("Error: computer player not set up before use.");
	    return;
	}
	Thread thisThread= Thread.currentThread(); // variables to allow the thread to be
	signal=thisThread;                         // stopped safely

	Thread t=Thread.currentThread(); // instance to allow access to Thread methods

	int movesFound;
	int moveCoords[][]=new int[25][3];//up to 25 (x,y,score) triplets giving possible moves
	// How many moves are possible in theory? Have seen up to 21 in practice, so far

	boolean noMoves=true;

	initBoard(aboard,iboard); 
        // make copy of board, and convert to index representation

	if(solving && (64-pieces<solveDepth)) {depth=20;System.out.println("Solving...");} 
        // far enough to reach the endgame
	//i.e. we solve the game with 15 to go.

	for(int i=0; i<udarray.length; i++){udarray[i]=new undoData();}
        //fill array with objects so they don't need to be created on the fly during the 
	//search process (creating objects is an expensive operation).  We need a different 
	//object for each search level, so we just index into the array according to our level.

	undoData ud=udarray[depth]; // data to enable us to undo moves
	//movesFound=findMoves(moveCoords,rootPlayer); // find and count the legal moves
	movesFound=iFindMoves(iboard,moveCoords,rootPlayer); // testing new version - seems OK


	/* -------------------------------main loop----------------------------------- */

	if(movesFound>1){ // no point searching if there's only one move!
	    //for(int deep=depth-3;deep<depth;deep+=2){ 
	    // disable iterative deepening of search tree
	    // because it currently slows the game down!
	    // probably because evaluation function is unstable                  
	    //System.out.println("Depth = "+deep);
	    int alpha=-1000000,beta=1000000,value=0; 
	    // search window wider than any possible return score

	    if(solving && (64-pieces<solveDepth)){
		alpha=-1; beta=1;
		System.out.println("Player: "+rootPlayer);} // zero-window search for WLD
	    // This endgame solving is a bit crude.
	    // The depth at which solving begins should be adjustable.

	    if(solving && (64-pieces<(solveDepth-1))){alpha=-1000000;beta=1000000;} // solve for best win

	    for(int move=0;move<movesFound;move++){
		iMakeMove(moveCoords[move][0],moveCoords[move][1],rootPlayer,ud);

		value=-negamax(-beta,-alpha,-rootPlayer,depth-1,false); //begin recursion
		if(value>alpha) alpha=value;
		moveCoords[move][2]=value;
		if(solving && (64-pieces<solveDepth)) 
                System.out.println("Move: "+moveCoords[move][0]+","+moveCoords[move][1]+" Score: "+value);
	
		
		iUndoMove(ud);

		if(solving && (64-pieces==(solveDepth-1)) && alpha==1) {movesFound=move;break;}
		// bit if a hack to break out of loop as soon as a win found if WLD solving

		if(signal!=thisThread) return; // check this thread hasn't been told to die 
		t.yield(); // allow minimal cooperation on non-timeslicing platforms
	    }

	// Save the solved board, player and score to a database
		if(solving && savingSolved && 64-pieces<(solveDepth-1) && 64-pieces>9) 
                   saveToDatabase(iboard,rootPlayer,alpha);

	    sortMoves(moveCoords,movesFound); 
            // with current evaluation this doesn't actually help!
	    //}
	}
	else if(movesFound==0)
	    {
		System.out.println("Error: computer found no moves.");
		return; // The computer can't move. This indicates an error because
		// this method should only be called if there is a move available
	    }
  
	/* ---------------------------end main loop----------------------------------- */

	// tell board to make the move
	// moves are sorted, so best move is first in moveCoords array 

	if(signal==thisThread){  // if this thread is still supposed to be running...
	    setChanged(); // set Observable flag...
	    int move[]=new int[3]; // setup object to return to Observer
            move[0]=moveCoords[0][0];
	    move[1]=moveCoords[0][1];
	    move[2]=rootPlayer;
	    notifyObservers(move); // and send move to Observer(s)
	}
	return;
    } // thread terminates



    /* --------------------------------------------------------------------------- */
    /*   Recursive negamax algorithm with alpha-beta pruning - index method        */
    /* --------------------------------------------------------------------------- */

    int negamax(int alpha, int beta, int player, int level,boolean passed){
	//System.out.println("Debug: negamax level "+level);

	if(level<=0) {return assess(player);} //if leafnode evaluate board and return score
	// NB if this also happens to be an end-of-game node, we won't return
	// end_assess() as expected. This is, ahem, a feature.  In serious games
	// endgame solving will be used, which will bypass the problem.

	undoData ud=udarray[level];  // get a spare undoData object to enable us to undo moves
	int value;
	boolean noMoves=true;
	int startX=1,startY=1;

	while(iMakeNextMove2(startX,startY,ud,player)){
	    startX=ud.moveX; startY=ud.moveY; // how far did we get around board
	    noMoves=false; // we found a move!

	    value=-negamax(-beta,-alpha,-player,level-1,false);//recurse to next level of search
	    iUndoMove(ud);

	    if(value>=beta) return value; // prune search tree
	    if(value>alpha) alpha=value; // remember maximum so far - adjust search window
	    startY++; if(startY>8){startY=1;startX++;} // update startX,startY
	}
	    
	if(noMoves){
	    if(passed) return end_assess(player); // if neither player can move, game has ended
	    else{
		value=-negamax(-beta,-alpha,-player,level-1,true); 
		//recurse to next level of search   
		if(value>=beta) return value; // prune search tree
		if(value>alpha) alpha=value; // remember maximum so far
	    }
	}

	return alpha;
    }

  
    /* --------------------------------------------------------------------------- */
    /**
       Make next valid move on the  board, looking from a given starting point.
       Also stores data to allow us to undo this move later with iUndoMove() */
    /* Avoids wastefully finding all moves when you might return after just one.   
       Stores undo data in the searchBoard object for the undoMove method.         
       Also avoids making a method call for each of up to 60 squares!   
       The calling method needs to extract the x,y coords from the undoData
       object when this method returns, so we can start where we left off
       on the next call */          


    boolean iMakeNextMove2(int startX, int startY, undoData ud, int player){

	int i,xx,yy,flips,yloop,mask;
	int p,length;
	//	boolean legal;

	if(player==BLACK) p=0;
	else p=1;

	// Scan through board starting where we left off.
	// NB if this is the start of a fresh board position startX,startY must be
	// set to 1,1 by the calling method.

	yloop=startY;
	for(int x=startX; x<9; x++){
	    if(x>startX)yloop=1; // after first time round outer loop, expand inner loop
	    for(int y=yloop; y<9; y++){

		if(iboard.array[x][y]!=0) {continue;} // can't move on full square!

		ud.numFlips=0; 
		flips=0;

		// get all the flips and store them in the undoData object 

		xx=x; yy=y; 

                if((1 & ((legalTable[p][iboard.row[y-1]])>>x-1))==1){

		// check right (increasing x)
		    //xx=x; yy=y;
		while(iboard.array[++xx][yy]==-player){}
		if(iboard.array[xx--][yy]==player){ // have trapped some enemy pieces
		    while(xx!=x){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx--][yy]=player;}
		}

		// check left (decreasing x)
		xx=x; /* yy=y; */
		while(iboard.array[--xx][yy]==-player){}
		if(iboard.array[xx++][yy]==player){ // have trapped some enemy pieces
		    while(xx!=x){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx++][yy]=player;}
		}
		}

		if((1 & ((legalTable[p][iboard.column[x-1]])>>y-1))==1){

		// check down (increasing y)
		xx=x; /* yy=y; */
		while(iboard.array[xx][++yy]==-player){}
		if(iboard.array[xx][yy--]==player){ // have trapped some enemy pieces
		    while(yy!=y){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx][yy--]=player;}
		}

		// check up (decreasing y)
		/* xx=x; */ yy=y;
		while(iboard.array[xx][--yy]==-player){}
		if(iboard.array[xx][yy++]==player){ // have trapped some enemy pieces
		    while(yy!=y){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx][yy++]=player;}
		}
		}
		if(x>y)length=y-1;     
 		else length = x-1; 
		if((1 & ((legalTable[p][iboard.diag2[(7-x)+y]])>>length))==1){

		// check down right 
		xx=x; yy=y; 
		while(iboard.array[++xx][++yy]==-player){}
		if(iboard.array[xx--][yy--]==player){ // have trapped some enemy pieces
		    while(yy!=y){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx--][yy--]=player;}
		}

		// check up left 
		xx=x; yy=y;
		while(iboard.array[--xx][--yy]==-player){}
		if(iboard.array[xx++][yy++]==player){ // have trapped some enemy pieces
		    while(yy!=y){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx++][yy++]=player;}
		}
		}
 		if((9-x)>y)length=y-1;
 		else length=(8-x);
		if((1 & ((legalTable[p][iboard.diag1[x+y-2]])>>length))==1){

		// check down left 
		xx=x; yy=y;
		while(iboard.array[--xx][++yy]==-player){}
		if(iboard.array[xx++][yy--]==player){ // have trapped some enemy pieces
		    while(yy!=y){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx++][yy--]=player;}
		}

		// check up right 
		xx=x; yy=y;
		while(iboard.array[++xx][--yy]==-player){}
		if(iboard.array[xx--][yy++]==player){ // have trapped some enemy pieces
		    while(yy!=y){
			ud.flipX[flips]=xx;ud.flipY[flips++]=yy;
			iboard.array[xx--][yy++]=player;}
		}
		}

		// if the move is legal (ie we flipped something) place new piece 
		// otherwise go on to next iteration

		if(flips<=0) continue;
		ud.numFlips=flips;
		iboard.array[x][y]=player; // place new piece 
		ud.byWhom=player;    // undo data
		ud.moveX=x; ud.moveY=y; // undo data

		// update the indices for that piece using x,y
                if(player==BLACK){
		    //row
		    iboard.row[y-1]+=placeOffsetTableB[x-1]; // p just changes the sign
		    //column
		    iboard.column[x-1]+=placeOffsetTableB[y-1];
		    //diag1
		    if(9-x>y) iboard.diag1[x+y-2]+=placeOffsetTableB[y-1];
		    else      iboard.diag1[x+y-2]+=placeOffsetTableB[8-x];
		    //diag2 
		    if(x>y) iboard.diag2[(7-x)+y]+=placeOffsetTableB[y-1];
		    else    iboard.diag2[(7-x)+y]+=placeOffsetTableB[x-1];
		}
		else{
		    //row
		    iboard.row[y-1]+=placeOffsetTableW[x-1]; // p just changes the sign
		    //column
		    iboard.column[x-1]+=placeOffsetTableW[y-1];
		    //diag1
		    if(9-x>y) iboard.diag1[x+y-2]+=placeOffsetTableW[y-1];
		    else      iboard.diag1[x+y-2]+=placeOffsetTableW[8-x];
		    //diag2 
		    if(x>y) iboard.diag2[(7-x)+y]+=placeOffsetTableW[y-1];
		    else    iboard.diag2[(7-x)+y]+=placeOffsetTableW[x-1];
		}

		// do the actual flips on the board array and 
		// update the 4 indices for each piece
		// using stored x,y pairs

		if(player==BLACK){
		    for(i=0;i<flips;i++){
			xx=ud.flipX[i]; yy=ud.flipY[i]; 
			// use these a lot; second-guess the compiler

			//row
			iboard.row[yy-1]+=flipOffsetTableB[xx-1]; // p just changes the sign
			//column
			iboard.column[xx-1]+=flipOffsetTableB[yy-1];
			//diag1
			if(9-xx>yy) iboard.diag1[xx+yy-2]+=flipOffsetTableB[yy-1];
			else        iboard.diag1[xx+yy-2]+=flipOffsetTableB[8-xx];
			//diag2 
			if(xx>yy) iboard.diag2[(7-xx)+yy]+=flipOffsetTableB[yy-1];
			else      iboard.diag2[(7-xx)+yy]+=flipOffsetTableB[xx-1];
		    }
		}
		else{
		    for(i=0;i<flips;i++){
			xx=ud.flipX[i]; yy=ud.flipY[i]; 
			// use these a lot; second-guess the compiler

			//row
			iboard.row[yy-1]+=flipOffsetTableW[xx-1]; // p just changes the sign
			//column
			iboard.column[xx-1]+=flipOffsetTableW[yy-1];
			//diag1
			if(9-xx>yy) iboard.diag1[xx+yy-2]+=flipOffsetTableW[yy-1];
			else        iboard.diag1[xx+yy-2]+=flipOffsetTableW[8-xx];
			//diag2 
			if(xx>yy) iboard.diag2[(7-xx)+yy]+=flipOffsetTableW[yy-1];
			else      iboard.diag2[(7-xx)+yy]+=flipOffsetTableW[xx-1];
		    }

		}
		return true; // we found and made a move
	    }
	}
	return false; // no more legal moves found
    }




    /* --------------------------------------------------------------------------- */
    /* make a move on the board using indices - doesn't check if it is legal       */
    /* --------------------------------------------------------------------------- */

    void iMakeMove(int x, int y, int player, undoData ud){
	int xx,yy,i,mask,index,length,p;
	ud.numFlips=0; // number of pieces flipped by this move so far

	if(player==BLACK) p=0;
	else p=1;

	// Calculate the row, column and diags affected by the move, and the position
	// of the move along them, then look up an int (may be byte, later) with bits set
	// to show us which pieces are flipped over. Store these flips in the undoData
	// object.

	//Note that we only loop from 1 to 6, not 0 to 7, because a move on any 
	//row/column/diagonal cannot flip the piece at the end. Could also shorten 
	// loop for diagonals shorter than 8. todo.

 	// There's a lot of fiddling around here - 
	// might be quicker to have (another!) lookup table

	// Is this really quicker than working out the flips
	// normally???  NO. This code is slower, but is only run once per
	// move, so isn't a priority for improvement.

	// NB although x and y are from 1 to 8, the array accesses have to be from 0 to 7.

	// row

        // look up contents of the correct row
	mask=flipsTable[p][iboard.row[y-1]][x-1];
	for(i=1;i<7;i++){
	    if((mask>>i & 1)==1) {ud.flipX[ud.numFlips]=i+1; ud.flipY[ud.numFlips++]=y;}
	}

	// column

	mask=flipsTable[p][iboard.column[x-1]][y-1];
	for(i=1;i<7;i++){
	    if((mask>>i & 1)==1) {ud.flipX[ud.numFlips]=x; ud.flipY[ud.numFlips++]=i+1;}
	}

	// diag1 -////-

	if(9-x>y) mask=flipsTable[p][iboard.diag1[x+y-2]][y-1];
        else      mask=flipsTable[p][iboard.diag1[x+y-2]][8-x];	   
	for(i=1;i<7;i++){
	    if((mask>>i & 1)==1) {
		if(9-x>y) {ud.flipX[ud.numFlips]=(x+y-1)-i; ud.flipY[ud.numFlips++]=i+1;}
		else      {ud.flipX[ud.numFlips]=8-i; ud.flipY[ud.numFlips++]=y-(8-x)+i;}
	    }
	}


	// diag2 -\\\\-

	if(x>y) mask=flipsTable[p][iboard.diag2[(7-x)+y]][y-1];
	else    mask=flipsTable[p][iboard.diag2[(7-x)+y]][x-1];
	for(i=1;i<7;i++){
	    if((mask>>i & 1)==1) {
		if(x>y) {ud.flipX[ud.numFlips]=(x-y)+1+i; ud.flipY[ud.numFlips++]=1+i;}
		else    {ud.flipX[ud.numFlips]=1+i; ud.flipY[ud.numFlips++]=(y-x)+1+i;}
	    }
	}


	// place new piece 
	iboard.array[x][y]=player;  
	ud.byWhom=player;    // undo data
	ud.moveX=x; ud.moveY=y; // undo data

	// update the indices for that piece using x,y

	//row
	iboard.row[y-1]+=placeOffsetTable[p][x-1]; // only thing that p changes is the sign

	//column
	iboard.column[x-1]+=placeOffsetTable[p][y-1];

	//diag1
	if(9-x>y) iboard.diag1[x+y-2]+=placeOffsetTable[p][y-1];
	else      iboard.diag1[x+y-2]+=placeOffsetTable[p][8-x];

	//diag2 
	if(x>y) iboard.diag2[(7-x)+y]+=placeOffsetTable[p][y-1];
	else    iboard.diag2[(7-x)+y]+=placeOffsetTable[p][x-1];


	// do the actual flips on the board array and update the 4 indices for each piece
	// using stored x,y pairs

	// TODO - get speed increase by having separate code for black and white
	// and getting rid of the p array index

	for(i=0;i<ud.numFlips;i++){
   
	    xx=ud.flipX[i]; yy=ud.flipY[i]; 
	    // use these a lot, so second-guess the compiler...ooer!

	    iboard.array[xx][yy]=player; // flip the piece

	    //row
	    iboard.row[yy-1]+=flipOffsetTable[p][xx-1]; // only thing that p changes is the sign

	    //column
	    iboard.column[xx-1]+=flipOffsetTable[p][yy-1];

	    //diag1
	    if(9-xx>yy) iboard.diag1[xx+yy-2]+=flipOffsetTable[p][yy-1];
	    else        iboard.diag1[xx+yy-2]+=flipOffsetTable[p][8-xx];

	    //diag2 
	    if(xx>yy) iboard.diag2[(7-xx)+yy]+=flipOffsetTable[p][yy-1];
	    else      iboard.diag2[(7-xx)+yy]+=flipOffsetTable[p][xx-1];

	}

    }


    /* --------------------------------------------------------------------------- */
    /*  Undo a move using indices.                                                 */
    /*  NB this should not be called if the move was 'null' ie a 'pass'.           */
    /* --------------------------------------------------------------------------- */

    void iUndoMove(undoData ud){

	int xx,yy;
	//int p;
	xx=ud.moveX; yy=ud.moveY;  
	// use these a lot, so second-guess compiler!
	iboard.array[xx][yy]=0; // remove piece

	//if(ud.byWhom==BLACK) p=0;
	//else p=1;

	// update indices
	if(ud.byWhom==BLACK){
	    //row
	    iboard.row[yy-1]-=placeOffsetTableB[xx-1]; // only thing that p changes is the sign
	    //column
	    iboard.column[xx-1]-=placeOffsetTableB[yy-1];
	    //diag1
	    if(9-xx>yy)iboard.diag1[xx+yy-2]-=placeOffsetTableB[yy-1];
	    else iboard.diag1[xx+yy-2]-=placeOffsetTableB[8-xx];
	    //diag2 
	    if(xx>yy) iboard.diag2[(7-xx)+yy]-=placeOffsetTableB[yy-1];
	    else iboard.diag2[(7-xx)+yy]-=placeOffsetTableB[xx-1];
	}
	else{
	    //row
	    iboard.row[yy-1]-=placeOffsetTableW[xx-1]; // only thing that p changes is the sign
	    //column
	    iboard.column[xx-1]-=placeOffsetTableW[yy-1];
	    //diag1
	    if(9-xx>yy)iboard.diag1[xx+yy-2]-=placeOffsetTableW[yy-1];
	    else iboard.diag1[xx+yy-2]-=placeOffsetTableW[8-xx];
	    //diag2 
	    if(xx>yy) iboard.diag2[(7-xx)+yy]-=placeOffsetTableW[yy-1];
	    else iboard.diag2[(7-xx)+yy]-=placeOffsetTableW[xx-1];
	}
	for(int i=0; i<ud.numFlips; i++){ // loop for each piece flipped
	    xx=ud.flipX[i]; yy=ud.flipY[i];
	    iboard.array[xx][yy]=-ud.byWhom; 
	    // update indices
	    if(ud.byWhom==BLACK){
		//row
		iboard.row[yy-1]-=flipOffsetTableB[xx-1];
		//column
		iboard.column[xx-1]-=flipOffsetTableB[yy-1];
		//diag1
		if(9-xx>yy)iboard.diag1[xx+yy-2]-=flipOffsetTableB[yy-1];
		else iboard.diag1[xx+yy-2]-=flipOffsetTableB[8-xx];
		//diag2 
		if(xx>yy)iboard.diag2[(7-xx)+yy]-=flipOffsetTableB[yy-1];
		else iboard.diag2[(7-xx)+yy]-=flipOffsetTableB[xx-1];
	    }
	    else{
		//row
		iboard.row[yy-1]-=flipOffsetTableW[xx-1];
		//column
		iboard.column[xx-1]-=flipOffsetTableW[yy-1];
		//diag1
		if(9-xx>yy)iboard.diag1[xx+yy-2]-=flipOffsetTableW[yy-1];
		else iboard.diag1[xx+yy-2]-=flipOffsetTableW[8-xx];
		//diag2 
		if(xx>yy)iboard.diag2[(7-xx)+yy]-=flipOffsetTableW[yy-1];
		else iboard.diag2[(7-xx)+yy]-=flipOffsetTableW[xx-1];

	    }

	}
    }


    /* ---------------------------------------------------------------------- */

    int iFindMoves(Board iboard, int[][] a,int player){
	int m=0;

	for(int x=1; x<9; x++){
	    for(int y=1; y<9; y++){

		if(iboard.array[x][y]!=0) continue;// if square is full then it's illegal 

		if(i_legal(x,y,player)) {
		    a[m][0]=x; a[m++][1]=y;
		}
	    }
	}

	return m;
    } // end of method iFindMoves



    /* --------------------------------------------------------------------------- */
    /*                 sort array of moves inefficiently!                          */
    /*  This is a pretty nasty sort method, but there's usually less than 10 items */
    /* --------------------------------------------------------------------------- */

    void sortMoves(int[][] m, int entries){
	int temp0,temp1,temp2;

	for(int x=entries-1; x>0; x--){
	    for(int y=0;y<x;y++){
		if(m[y][2]<m[y+1][2]){ //swap
		    temp0=m[y][0]; temp1=m[y][1]; temp2=m[y][2];
		    m[y][0]=m[y+1][0]; m[y][1]=m[y+1][1]; m[y][2]=m[y+1][2];   // Ooh, yuk!
		    m[y+1][0]=temp0; m[y+1][1]=temp1; m[y+1][2]=temp2;
		}
	    }
	}
    }

    /* --------------------------------------------------------------------------- */
    /*          assess value of board for player at root of search tree            */
    /* --------------------------------------------------------------------------- */

    // A fairly feeble evaluation function which uses mobility, favours corners
    // and discourages X- and C-squares (the ones adjacent to corners). Much better, however,
    // than just counting pieces, or only assigning weights to squares.

    int assess(int player){
	int score=0;

	// first assess for black, then negate if necessary

// 	for(int x=1; x<9; x++){
// 		score+=iboard.array[x][1];
// 		score+=iboard.array[x][2];
// 		score+=iboard.array[x][3];
// 		score+=iboard.array[x][4];
// 		score+=iboard.array[x][5];
// 		score+=iboard.array[x][6];
// 		score+=iboard.array[x][7];
// 		score+=iboard.array[x][8];
// 	}


	score+=iboard.array[1][1]<<5;
	score-=iboard.array[2][2]<<3;
	score-=iboard.array[1][2]<<2;
	score-=iboard.array[2][1]<<2;

	score+=iboard.array[8][8]<<5;
	score-=iboard.array[7][7]<<3;
	score-=iboard.array[7][8]<<2;
	score-=iboard.array[8][7]<<2;

	score+=iboard.array[1][8]<<5;
	score-=iboard.array[2][7]<<3;
	score-=iboard.array[1][7]<<2;
	score-=iboard.array[2][8]<<2;

	score+=iboard.array[8][1]<<5;
	score-=iboard.array[7][2]<<3;
	score-=iboard.array[7][1]<<2;
	score-=iboard.array[8][2]<<2;

	if(player==BLACK)return mobility(iboard,BLACK)+score;
	else return mobility(iboard,WHITE)-score;
    }

    /* --------------------------------------------------------------------------- */
    /*     assess value of finished board for player at root of search tree        */
    /* --------------------------------------------------------------------------- */

    int end_assess(int player){
	int score=0;

	// first assess for black, then negate if necessary

	for(int x=1; x<9; x++){
		score+=iboard.array[x][1];
		score+=iboard.array[x][2];
		score+=iboard.array[x][3];
		score+=iboard.array[x][4];
		score+=iboard.array[x][5];
		score+=iboard.array[x][6];
		score+=iboard.array[x][7];
		score+=iboard.array[x][8];
	}

	if(score==0)return 0; //draw
  
	if(player==BLACK) return score<<8;  
	else return -(score<<8);
 // need to return scaled score so that endgame solving can find best win, not just any win,
	// but also want score of a close win to be greater than any score generated by the
	// normal assess routine, to give it more importance.
    }


    /* --------------------------------------------------------------------------- */
    /** Makes a copy of the board, and converts it to index representation */

    void initBoard(int array[][], Board iboard){


	// ********************************* WARNING ************************************** 
	// Don't use clone() as it makes a new object and something gets horribly screwed-up,
	// probably on the stack.  Copy arrays instead:

	for(int i=0;i<10;i++){
	    for(int j=0;j<10;j++){
		iboard.array[i][j]=array[i][j];  //should only need to copy 1 to 8
	    }
	}

	// Convert to index representation

	int digitwhite[]={4374,1458,486,162,54,18,6,2}; 
	int digitblack[]={2187,729,243,81,27,9,3,1};
	int digit[]; 
	int howfar;

	// indices should be initialised to zero when created

	for (int x=1;x<9;x++) /* columns */
	    {
		for (int y=1;y<9;y++) /* rows */
		    {

			if(array[x][y]==0) continue;
			if(array[x][y]==BLACK) {digit=digitblack;}
			else {digit=digitwhite;}

			iboard.column[x-1]+=digit[y-1];
			iboard.row[y-1]+=digit[x-1];
			if((9-x)>y)howfar=y-1; else howfar=(8-x);  
			iboard.diag1[x+y-2]+=digit[howfar];
			if(x>y)howfar=y-1; else howfar=x-1;      
			iboard.diag2[(7-x)+y]+=digit[howfar];
		    }
	    }

	return;
    }


    /* --------------------------------------------------------------------------- */
    /** Initialise look-up tables for move-finding move-making, and mobility */

    static void initTables(){
	int line[]=new int[8];
	int b_entry,w_entry,mobil,pmobil,index;
	int b_flips[]=new int[1];
	int w_flips[]=new int[1]; 

	for(int i=0;i<6561;i++){ // loop through all possible ternary values
	    b_entry = w_entry = b_flips[0] = w_flips[0]= mobil = pmobil = 0;

	    // Each ternary value corresponds to a different configuration of
	    // black, white, and empty squares along a line.  We then work out
	    // which moves (for black and white separately) on that line would
	    // actually flip over some enemy pieces, and hence indicate a legal move.

	    index=i;

	    if(index>=4374) {index-=4374;line[0]=WHITE;}
	    else if(index>=2187) {index-=2187;line[0]=BLACK;}
	    else line[0]=0;

	    if(index>=1458) {index-=1458;line[1]=WHITE;}
	    else if(index>=729) {index-=729;line[1]=BLACK;}
	    else line[1]=0;

	    if(index>=486) {index-=486;line[2]=WHITE;}
	    else if(index>=243) {index-=243;line[2]=BLACK;}
	    else line[2]=0;

	    if(index>=162) {index-=162;line[3]=WHITE;}
	    else if(index>=81) {index-=81;line[3]=BLACK;}
	    else line[3]=0;

	    if(index>=54) {index-=54;line[4]=WHITE;}
	    else if(index>=27) {index-=27;line[4]=BLACK;}
	    else line[4]=0;

	    if(index>=18) {index-=18;line[5]=WHITE;}
	    else if(index>=9) {index-=9;line[5]=BLACK;}
	    else line[5]=0;

	    if(index>=6) {index-=6;line[6]=WHITE;}
	    else if(index>=3) {index-=3;line[6]=BLACK;}
	    else line[6]=0;

	    if(index>=2) {index-=2;line[7]=WHITE;}
	    else if(index>=1) {index-=1;line[7]=BLACK;}
	    else line[7]=0;

	    for(int j=0;j<8;j++){ // for each position along the line...
		if(linelegal(line,j,BLACK,b_flips)) {b_entry |= (1<<j);mobil++;} /*set bit*/      
		if(linelegal(line,j,WHITE,w_flips)) {w_entry |= (1<<j);mobil--;} /*set bit*/
		flipsTable[0][i][j]=(byte)b_flips[0]; // store which pieces are flipped
		flipsTable[1][i][j]=(byte)w_flips[0]; // store which pieces are flipped

	    }        // todo: assess potential mobility
	    legalTable[0][i]=(byte)b_entry;  // store which positions were legal for Black
	    legalTable[1][i]=(byte)w_entry;  // store which positions were legal for White
	    mobilityTable[i]=mobil*MOBIL_WEIGHT; // + pmobil*POTEN_WEIGHT;
	}

	return;
    }

    /* --------------------------------------------------------------------------- */
    /** Method used in creating lookup tables. Finds whether any pieces are flipped 
	on a given direction by placing a piece of given colour at a given location.
	The coding in this method is thoroughly disgusting, but is so unimportant
	that I haven't got round to tidying it up, since it works fine.*/

    static boolean linelegal(int line[],int position,int colour,int flips[]){
	int loop,count;

	flips[0]=0;  //  hack to get a non-primitive variable that we can play with
	//  and get the results back

	// check if position is unoccupied

	if (line[position] != 0) return false;

	/* check left */
	if(position>1)
	    {
		if (line[position-1]==-colour)
		    {
			for(loop=position-2;loop>=0;loop--)
			    {
				if (line[loop]==colour) {
				    while(++loop<position) {flips[0] |=(1<<loop);}
				    //return true;
				    break;
				}
				else if (line[loop]==0) break;
			    }
		    }
	    }

	/* check right */
	if(position<6)
	    {
		if (line[position+1]==-colour)
		    {
			for(loop=position+2;loop<8;loop++)
			    {
				if (line[loop]==colour) {
				    while(--loop>position){flips[0] |=(1<<loop);} 
				    //return true;
				    break;
				}
				else if (line[loop]==0) break;
			    }
		    }
	    }
 
	if(flips[0]==0)return false;  /* ie haven't turned any counters yet */
	else {
	    return true;  
	}

    }


    /* --------------------------------------------------------------------------- */
    /** Calculates the mobility (and potentially other quantities lumped in) by table lookup */ 
    private int mobility(Board iboard,int player){

	//uses static int mobilityTable[6561];
	int mobil;

	/* add up mobilities for all rows and columns, and all diagonals larger      */
	/* than 3. Based on method from Logistello by Michael Buro (though I daresay */
	/* he's coded it faster! Counts mobility for black. */

	mobil=mobilityTable[iboard.row[0]];
	mobil+=mobilityTable[iboard.row[1]];
	mobil+=mobilityTable[iboard.row[2]];
	mobil+=mobilityTable[iboard.row[3]];
	mobil+=mobilityTable[iboard.row[4]];
	mobil+=mobilityTable[iboard.row[5]];
	mobil+=mobilityTable[iboard.row[6]];
	mobil+=mobilityTable[iboard.row[7]];

	mobil+=mobilityTable[iboard.column[0]];
	mobil+=mobilityTable[iboard.column[1]];
	mobil+=mobilityTable[iboard.column[2]];
	mobil+=mobilityTable[iboard.column[3]];
	mobil+=mobilityTable[iboard.column[4]];
	mobil+=mobilityTable[iboard.column[5]];
	mobil+=mobilityTable[iboard.column[6]];
	mobil+=mobilityTable[iboard.column[7]];

	mobil+=mobilityTable[iboard.diag1[3]];
	mobil+=mobilityTable[iboard.diag1[4]];
	mobil+=mobilityTable[iboard.diag1[5]];
	mobil+=mobilityTable[iboard.diag1[6]];
	mobil+=mobilityTable[iboard.diag1[7]];
	mobil+=mobilityTable[iboard.diag1[8]];
	mobil+=mobilityTable[iboard.diag1[9]];
	mobil+=mobilityTable[iboard.diag1[10]];
	mobil+=mobilityTable[iboard.diag1[11]];

	mobil+=mobilityTable[iboard.diag2[3]];
	mobil+=mobilityTable[iboard.diag2[4]];
	mobil+=mobilityTable[iboard.diag2[5]];
	mobil+=mobilityTable[iboard.diag2[6]];
	mobil+=mobilityTable[iboard.diag2[7]];
	mobil+=mobilityTable[iboard.diag2[8]];
	mobil+=mobilityTable[iboard.diag2[9]];
	mobil+=mobilityTable[iboard.diag2[10]];
	mobil+=mobilityTable[iboard.diag2[11]];

	if(player==BLACK)return mobil;
	else return -mobil;
    }


    /*-------------------------------------------------------------------*/
    /** check whether a specified move is legal.  Requires the board in
	index representation. */
    boolean i_legal(int column,int row,int colour)
    {
	int col,length;

	column--; // Want coords 0-7 not 1-8
	row--;    

	if (colour==BLACK) col=0;  // get rid of this extra array index
	else col=1;                // to speed method up!

	/* For specified square get the index for the row, column and diagonals 
	   Use the index to look up legal moves from table and see if they match 
	   the specified square

	   Table entry is an byte.
	   Rightmost bit corresponds to first square in row/column/diagonal */
  
	/* check column */

	//temp=legalTable[col][iboard.column[column]]; /* get entry from table */
	if((1 & ((legalTable[col][iboard.column[column]])>>row))==1) return true; 
        /* test relevant bit */

	/* check row */

	//temp=legalTable[col][iboard.row[row]]; /* get entry from table */
	if((1 & ((legalTable[col][iboard.row[row]])>>column))==1) return true; 
        /* test relevant bit */

	/* check diag1 */

	if(row<=(7-column))length=row;
	else length=(7-column);

	//temp=legalTable[col][iboard.diag1[column+row]]; /* get entry from table */
	if((1 & ((legalTable[col][iboard.diag1[column+row]])>>length))==1) return true; 
        /* test relevant bit */

	/* check diag2 \\\\ */

	if(row<=column)length=row;     
	else length = column; 

	//temp=legalTable[col][iboard.diag2[(7-column)+row]]; /* get entry from table */
	if((1 & ((legalTable[col][iboard.diag2[(7-column)+row]])>>length))==1) return true; 
        /* test relevant bit */

	return false;
    }



    void saveToDatabase(Board iboard,int rootPlayer, int value){
	long black=0;
	long white=0;  // encode board as two 64-bit numbers, one for each colour
	int bit=0;

	//byte player=(byte)rootPlayer;
	//byte value=(byte)value;

	for(int x=1;x<9;x++){
	    for(int y=1;y<9;y++){
		if(iboard.array[x][y]==BLACK) black |=1<<bit;
		else if(iboard.array[x][y]==WHITE) white |=1<<bit;
		bit++;
	    }
	}

	// save to file database.dat as binary data

	FileOutputStream fs;
	DataOutputStream ds;
	try{
	    fs=new FileOutputStream("database.dat", true); // open in append mode
	    ds = new DataOutputStream(fs);

	    ds.writeLong(black);
	    ds.writeLong(white);       // board
	    ds.writeByte(rootPlayer);  // player to move
	    ds.writeByte(value>>8);       // true value of board *for that player*

	    ds.close();
	}
	catch(IOException ioe){System.out.println("IO error whilst writing to database.dat: "+ioe);}


    }


} // end of class OthPlayerThread

/*

       ********* Ternary index board representation **********

This way of storing the board was taken from published papers by
Michael Buro, author of the world champion othello program Logistello.
A number of his papers are available online and I recommend them highly.

Each row, column and diagonal on the board is represented by one
single number, which can then be used to access a lookup table, both
to check for legal moves, and to evaluate the board.  It enables
several quantities commonly used in evaluating the board (mobility,
potential mobility, and actual position) to be estimated together very
quickly in one operation, by looking up one value for each row, column
and diagonal.  This gives a vast speed improvement over trying to
evaluate mobility and all the other quantities in a naive way.

Each square can have three values - empty, black, white.  Each row of
up to eight squares can therefore be represented by an eight-digit
ternary (base 3) number (index).  Of course, we just store this as a
decimal or binary value, by adding up the values for each square:

e.g:

white: 4374,1458,486,162,54,18,6,2 
black: 2187,729,243,81,27,9,3,1


Buro found that updating the indices incrementally (ie each time the
board changes) was faster than updating the plain 10x10 array and then
re-creating all the indices when needed.  He also found that (on
current workstations) keeping only one copy of the board, making and
then undoing moves was faster than copying the board entirely and
preserving the original, due to the memory access required.

The 8x8 board is stored in a 10x10 array with a blank border because
this simplifies the loops in various method, removing the need to test
for the edge of the board - the loops terminate naturally when they
reach the blank outside square.

It is actually possible to dispense with the 10x10 array entirely, and
just use indices, but I don't think this is worthwhile, or even very quick
especially as the simplest way to store the undo information appears
to be to list the pieces actually flipped, rather than the (more
numerous) indices changed.

              *************** Negamax Gotcha *****************

Negamax is an equivalent algorithm to MiniMax. Minimax is quite simple
to explain, but it can get a bit mind-bending once you start using
recursive methods.  Explanations of Negamax often say that at the leaf nodes
you score the board "for the current player".  This is rather ambiguous, as
you can interpret this to mean "the player at the root of the search tree, 
whose best move we are searching for".  This is NOT what is meant - it means
"the current player at the leaf node".  

For an explanation of minimax, do a web search; there are a lot of tutorials around.
Suggested keywords for search: minimax negamax alpha beta pruning chess othello

The basic concept is that we play safe by assuming that at each move (as we look ahead 
from the current position) each player makes the best possible move. One player will try
to maximise his score, whilst his opponent will try to minimise it (hence, MiniMax).

Negamax/Minimax is by no means the only possible method.  There are a
whole range of related methods; NegaScout, A*, B*, MDT-f, SSS*, DUAL*,
etc.  Research papers on these methods are available online, if you
search for them.

                       ******** Glossary *********

Some Othello terms explained:

C-square: A square orthogonally adjacent to a corner (see also X-square)

Mobility: The number of legal moves available to a player 
(usually, the number available to the player, minus the number available to the opponent)

Potential Mobility: The number of squares which are empty, and adjacent to an opponents piece,
and may therefore become legal moves at some point in the future.  In evaluating this 
quantity, squares which are _already_ legal may not be counted.

X-square: A square diagonally adjacent to a corner

*/
