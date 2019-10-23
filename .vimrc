call plug#begin()

" On-demand loading
Plug 'aklt/plantuml-syntax'
call plug#end()
if &diff
    " diff mode"
    set diffopt+=iwhite
    set mouse=a
    map ] ]c
    map [ [c
endif
set nu
set tabstop=4
set shiftwidth=4
set smarttab
set autoindent
set expandtab
set cursorline
set hlsearch
set nocompatible
set nowrapscan
set clipboard=unnamedplus
set wildignore+=*/node_modules/*
" from home
syntax on " Enable syntax highlighting.
filetype plugin indent on " Enable file type based indentation.
set autoindent " Respect indentation when starting a new line.
set expandtab " Expand tabs to spaces. Essential in Python.
set tabstop=4 " Number of spaces tab is counted for.
set shiftwidth=4 " Number of spaces to use for autoindent.
set backspace=2 " Fix backspace behavior on most terminals.
set tags=tags;
colorscheme industry " Change a colorscheme.

