#!/usr/bin/env python
import RPi.GPIO as GPIO
import sys

PINnum = 12

GPIO.setmode(GPIO.BOARD)
GPIO.setup(PINnum, GPIO.OUT)

argvs = sys.argv
argc = len(argvs)

if(argc != 2):
	quit()

if (argvs[1]=="Y"):
	GPIO.output(PINnum, True)
elif(argvs[1]=="N"):
	GPIO.output(PINnum, False)


