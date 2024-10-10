{ pkgs ? import <nixpkgs> {} }:
pkgs.mkShell rec {
    buildInputs = with pkgs; [
    	clojure
			leiningen
			vagrant
    	just
			gnuplot
			nodejs 
    ];
}
