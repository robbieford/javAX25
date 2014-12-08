#!/usr/bin/env python
# -*- coding: utf-8 -*-

# ***************************************************************************
# *   Copyright (C) 2011, Paul Lutus                                        *
# *                                                                         *
# *   This program is free software; you can redistribute it and/or modify  *
# *   it under the terms of the GNU General Public License as published by  *
# *   the Free Software Foundation; either version 2 of the License, or     *
# *   (at your option) any later version.                                   *
# *                                                                         *
# *   This program is distributed in the hope that it will be useful,       *
# *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
# *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
# *   GNU General Public License for more details.                          *
# *                                                                         *
# *   You should have received a copy of the GNU General Public License     *
# *   along with this program; if not, write to the                         *
# *   Free Software Foundation, Inc.,                                       *
# *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
# ***************************************************************************

from biquad_module import Biquad

from pylab import *

from numpy import genfromtxt

import re

sample_rate = 48000.0 # sampling frequency

pll_integral = 0
pll_lock = 0
pll_cf = 1700
pll_loop_gain = 4
ref_sig = 0

invsqr2 = 1.0 / math.sqrt(2.0)
#invsqr2 = 4.0

output_lowpass = Biquad(300,48000,invsqr2)

fa = []
da = []
testSig = []
pllCont = []

#myData = genfromtxt('noise.csv', delimiter=",")
myData = genfromtxt('gen200Packet.csv', delimiter=",")
#myData = genfromtxt('noisePacket.csv', delimiter=",")

for n in range(0,len(myData)):
  t = n / sample_rate
  
  # BEGIN test signal bloc
  test_signal = myData[n%len(myData)]
  # END test signal block
  
  # BEGIN PLL block
  pll_loop_control = test_signal * ref_sig * pll_loop_gain
  output = output_lowpass(pll_loop_control)
  pll_integral += pll_loop_control / sample_rate
  ref_sig = math.sin(2 * math.pi * pll_cf * (t + pll_integral))
  # END PLL block
  
  fa.append(n)
  da.append(output*20)
  testSig.append(test_signal)
  pllCont.append(pll_loop_control*8)

pllAvg = []

for n in range(0, len(pllCont)):
  if n < 13:
    pllAvg.append(0)
  else:
    sum = 0
    for j in range(-13, 0):
      sum+=pllCont[n+j]
    pllAvg.append(sum / 13)


plot(fa, testSig, 'g', fa, da, 'b', fa, pllCont, 'r')
#ylim(-1,1)
#grid(True)
#locs, labels = xticks()
#setp(labels,fontsize=8)
#locs, labels = yticks()
#setp(labels,fontsize=8)
#yticks([-1,-0.75,-0.5,-0.25,0,0.25,0.5,0.75,1])
gcf().set_size_inches(4,3)

name = re.sub('.*?(\w+).*','\\1',sys.argv[0])
#savefig(name+'.png')

show()
